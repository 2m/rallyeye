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

import scala.concurrent.duration._
import scala.io.Source

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
        yield rally(
          rallyId,
          n,
          s"https://www.rallysimfans.hu/rbr/rally_online.php?centerbox=rally_list_details.php&rally_id=$rallyId",
          r
        )
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

def pressAutoLogic(year: Int): IO[Either[Unit, RallyData]] =
  val response = for {
    year <- if year == 2023 then Right(year) else Left(())
    csv = Source.fromResource(s"pressauto$year.csv")(scala.io.Codec.UTF8).mkString
    results = parsePressAuto(csv)
  } yield rally(year, s"Press Auto $year", s"https://raceadmin.eu/pr${year}/pr${year}/results/overall/all", results)
  IO.pure(response)

object Data extends IOApp.Simple:
  def run: IO[Unit] =
    import cats.syntax.semigroupk._
    val interp = Http4sServerInterpreter[IO]()
    val routes = (interp.toRoutes(dataEndpoint.serverLogic(dataLogic)) <+> interp.toRoutes(
      pressAutoEndpoint.serverLogic(pressAutoLogic)
    )).orNotFound

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
