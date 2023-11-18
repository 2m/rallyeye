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

import cats.data.EitherT
import cats.effect.IO
import cats.effect.LiftIO
import com.comcast.ip4s._
import org.http4s.CacheDirective
import org.http4s.Method
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Caching
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.GZip
import rallyeye.shared._
import rallyeye.storage.Repo
import sttp.tapir._
import sttp.tapir.server.http4s.Http4sServerInterpreter

val Timeout = 2.minutes

object Logic:
  val RallyNotStored = Error("Rally not stored")

  object Rsf:
    private def fetchAndStore(rallyId: Int) =
      EmberClientBuilder
        .default[IO]
        .withTimeout(Timeout)
        .withIdleConnectionTime(Timeout)
        .build
        .use { client =>
          (for {
            name <- EitherT(rallyeye.Rsf.rallyName(client, rallyId))
            results <- EitherT(rallyeye.Rsf.rallyResults(client, rallyId))
            _ <- EitherT(Repo.Rsf.saveRallyName(rallyId, name))
            _ <- EitherT(Repo.Rsf.saveRallyResults(rallyId, results))
          } yield name).value
        }

    def data(rallyId: Int) =
      for
        maybeRally <- EitherT(Repo.Rsf.getRally(rallyId))
        rally <- EitherT(
          maybeRally.fold(IO.pure(Left(RallyNotStored)))(rally => IO.pure(Right(rally)))
        )
        rallyResults <- EitherT(Repo.Rsf.getRallyResults(rallyId))
      yield rallyData(rally, rallyResults)

    def refresh(rallyId: Int) =
      for
        _ <- EitherT(fetchAndStore(rallyId))
        storedData <- data(rallyId)
      yield storedData

  object PressAuto:
    def data(year: Int) =
      for
        maybeRallyName <- EitherT(Repo.PressAuto.getRally(year))
        rally <- EitherT(
          maybeRallyName.fold(IO.pure(Left(RallyNotStored)))(rally => IO.pure(Right(rally)))
        )
        rallyResults <- EitherT(Repo.PressAuto.getRallyResults(year))
      yield rallyData(rally, rallyResults)

def handleErrors[T](f: IO[Either[Throwable, T]]) =
  f.map(_.left.map {
    case Logic.RallyNotStored => RallyNotStored()
    case t                    => GenericError(t.getMessage)
  })

val httpServer =
  import cats.syntax.semigroupk._
  val interp = Http4sServerInterpreter[IO]()
  val endpoints =
    List(
      Endpoints.Rsf.data.serverLogic((Logic.Rsf.data _).andThen(_.value).andThen(handleErrors)),
      Endpoints.Rsf.refresh.serverLogic((Logic.Rsf.refresh _).andThen(_.value).andThen(handleErrors)),
      Endpoints.PressAuto.data.serverLogic((Logic.PressAuto.data _).andThen(_.value).andThen(handleErrors))
    )

  val routes = endpoints.map(interp.toRoutes).reduce(_ <+> _).orNotFound

  for server <- EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withIdleTimeout(Timeout)
      .withHttpApp(
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
      .build
      .use(_ => IO.never)
  yield server
