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

import scala.concurrent.duration.*

import cats.data.EitherT
import cats.effect.IO
import cats.effect.LiftIO
import com.comcast.ip4s.*
import org.http4s.CacheDirective
import org.http4s.Method
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Caching
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.GZip
import rallyeye.shared.*
import rallyeye.storage.Repo
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter

val Timeout = 2.minutes
val IdleTimeout = 3.minutes

object Logic:
  val RallyNotStored = Error("Rally not stored")
  val RallyInProgress = Error("Rally in progress")

  object Rsf:
    private def fetchAndStore(rallyId: String) =
      EmberClientBuilder
        .default[IO]
        .withTimeout(Timeout)
        .withIdleConnectionTime(IdleTimeout)
        .build
        .use { client =>
          (for
            info <- rallyeye.Rsf.rallyInfo(client, rallyId)
            results <- EitherT(rallyeye.Rsf.rallyResults(client, rallyId))
            _ <- EitherT(Repo.Rsf.saveRallyInfo(rallyId, info))
            _ <- EitherT(Repo.Rsf.saveRallyResults(rallyId, results))
          yield ()).value
        }

    def data(rallyId: String) =
      for
        maybeRally <- EitherT(Repo.Rsf.getRally(rallyId))
        rally <- EitherT(
          maybeRally.fold(IO.pure(Left(RallyNotStored)))(rally => IO.pure(Right(rally)))
        )
        rallyResults <- EitherT(Repo.Rsf.getRallyResults(rallyId))
      yield rallyData(rally, rallyResults)

    def refresh(rallyId: String) =
      for
        maybeRally <- EitherT(Repo.Rsf.getRally(rallyId))
        needToFetch = maybeRally.fold(true)(_.retrievedAt.plusSeconds(60).isBefore(Instant.now))
        _ <- EitherT(if needToFetch then fetchAndStore(rallyId) else IO.pure(Right("")))
        storedData <- data(rallyId)
      yield storedData

  object PressAuto:
    def data(year: String) =
      for
        maybeRallyName <- EitherT(Repo.PressAuto.getRally(year))
        rally <- EitherT(
          maybeRallyName.fold(IO.pure(Left(RallyNotStored)))(rally => IO.pure(Right(rally)))
        )
        rallyResults <- EitherT(Repo.PressAuto.getRallyResults(year))
      yield rallyData(rally, rallyResults)

  object Ewrc:
    private def fetchAndStore(rallyId: String) =
      EmberClientBuilder
        .default[IO]
        .withTimeout(Timeout)
        .withIdleConnectionTime(IdleTimeout)
        .build
        .use { client =>
          (for
            info <- rallyeye.Ewrc.rallyInfo(client, rallyId)
            results <- rallyeye.Ewrc.rallyResults(client, rallyId)
            _ <- EitherT(Repo.Ewrc.saveRallyInfo(rallyId, info))
            _ <- EitherT(Repo.Ewrc.saveRallyResults(rallyId, results))
          yield ()).value
        }

    def data(rallyId: String) =
      for
        maybeRally <- EitherT(Repo.Ewrc.getRally(rallyId))
        rally <- EitherT(
          maybeRally.fold(IO.pure(Left(RallyNotStored)))(rally => IO.pure(Right(rally)))
        )
        rallyResults <- EitherT(Repo.Ewrc.getRallyResults(rallyId))
      yield rallyData(rally, rallyResults)

    def refresh(rallyId: String) =
      for
        maybeRally <- EitherT(Repo.Ewrc.getRally(rallyId))
        needToFetch = maybeRally.fold(true)(_.retrievedAt.plusSeconds(60).isBefore(Instant.now))
        _ <- EitherT(if needToFetch then fetchAndStore(rallyId) else IO.pure(Right("")))
        storedData <- data(rallyId)
      yield storedData

def handleErrors[T](f: IO[Either[Throwable, T]]) =
  f.map(_.left.map {
    case Logic.RallyNotStored  => RallyNotStored()
    case Logic.RallyInProgress => RallyInProgress()
    case t                     => GenericError(t.getMessage)
  })

val httpServer =
  import cats.syntax.semigroupk.*

  for
    refreshShardedStreamAndLogic <- shardedLogic(5)(Logic.Rsf.refresh.andThen(_.value).andThen(handleErrors))
    (refreshShardedStream, refreshShardedLogic) = refreshShardedStreamAndLogic
    _ <- refreshShardedStream.compile.drain.start
    server = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromString(sys.env.getOrElse("RALLYEYE_SERVER_PORT", "8080")).get)
      .withIdleTimeout(Timeout)
      .withHttpApp(
        GZip(
          CORS.policy.withAllowOriginAll(
            Caching.cache(
              3.hours,
              isPublic = Left(CacheDirective.public),
              methodToSetOn = _ == Method.GET,
              statusToSetOn = _.isSuccess, {
                val interp = Http4sServerInterpreter[IO]()
                val endpoints =
                  List(
                    Endpoints.Rsf.data.serverLogic(Logic.Rsf.data.andThen(_.value).andThen(handleErrors)),
                    Endpoints.Rsf.refresh.serverLogic(refreshShardedLogic),
                    Endpoints.PressAuto.data
                      .serverLogic(Logic.PressAuto.data.andThen(_.value).andThen(handleErrors)),
                    Endpoints.Ewrc.data.serverLogic(Logic.Ewrc.data.andThen(_.value).andThen(handleErrors)),
                    Endpoints.Ewrc.refresh.serverLogic(Logic.Ewrc.refresh.andThen(_.value).andThen(handleErrors))
                  )
                endpoints.map(interp.toRoutes).reduce(_ <+> _).orNotFound
              }
            )
          )
        )
      )
      .build
  yield server
