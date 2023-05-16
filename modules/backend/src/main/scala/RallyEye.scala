/*
 * Copyright 2022 github.com/2m/rallyeye/contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rallyeye

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import cats.effect.IO
import cats.effect.IOApp
import cats.effect.LiftIO
import com.comcast.ip4s._
import io.chrisdavenport.mules.caffeine.CaffeineCache
import io.chrisdavenport.mules.http4s.CacheItem
import io.chrisdavenport.mules.http4s.CacheMiddleware
import io.chrisdavenport.mules.http4s.CacheType
import org.http4s.CacheDirective
import org.http4s.Method
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Caching
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.GZip
import rallyeye.shared._
import sttp.tapir._
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.server.http4s.Http4sServerInterpreter

val Timeout = 2.minutes

def dataLogic(rallyId: Int): IO[Either[Unit, RallyData]] =
  EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(Timeout)
    .build
    .use { client =>
      IO.both(rallyName(client, rallyId), rallyResults(client, rallyId)).map { case (name, results) =>
        for
          n <- name
          r <- results
        yield rally(n, r)
      }
    }

def rallyName(client: Client[IO], rallyId: Int): IO[Either[Unit, String]] =
  val (request, parseResponse) = Rsf.rallyName(rallyId)
  for
    response <- client.run(request).use(parseResponse(_))
    rallyName = response.map { body =>
      val regexp = "Final standings for: (.*)<br>".r
      regexp.findFirstMatchIn(body).get.group(1)
    }
  yield rallyName

def rallyResults(client: Client[IO], rallyId: Int): IO[Either[Unit, List[Entry]]] =
  val (request, parseResponse) = Rsf.rallyResults(rallyId)
  for
    response <- client.run(request).use(parseResponse(_))
    entries = response.map(parse)
  yield entries

object Data extends IOApp.Simple:
  def run: IO[Unit] =
    val routes = Http4sServerInterpreter[IO]().toRoutes(dataEndpoint.serverLogic(dataLogic)).orNotFound

    for
      caffeine <- CaffeineCache.build[IO, (Method, org.http4s.Uri), CacheItem](None, None, Some(100))
      cache = CacheMiddleware.httpApp(caffeine, CacheType.Public)
      server <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withIdleTimeout(Timeout)
        .withHttpApp(
          cache(
            GZip(
              CORS.policy.withAllowOriginAll(
                Caching.cache(
                  3.hours,
                  isPublic = Left(CacheDirective.public),
                  methodToSetOn = _ == Method.GET,
                  statusToSetOn = _.isSuccess,
                  routes
                )
              )
            )
          )
        )
        .build
        .use(_ => IO.never)
    yield ()
