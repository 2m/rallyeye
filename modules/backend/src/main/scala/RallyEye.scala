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

import cats.Monad
import cats.data.EitherT
import cats.data.Kleisli
import cats.effect.Spawn
import cats.effect.kernel.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import com.comcast.ip4s.*
import fs2.compression.Compression
import fs2.io.net.Network
import org.http4s.CacheDirective
import org.http4s.Method
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.Caching
import org.http4s.server.middleware.CORS
import org.http4s.server.middleware.GZip
import org.typelevel.otel4s.trace.StatusCode
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.*
import rallyeye.storage.Db
import rallyeye.storage.Repo
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.http4s.Http4sServerInterpreter

val Timeout = 2.minutes
val IdleTimeout = 3.minutes
val NumRefreshShards = 5

object Logic:
  sealed trait LogicError
  case object RallyNotStored extends Error("Rally not stored") with LogicError
  case object RallyInProgress extends Error("Rally in progress") with LogicError
  case object RefreshNotSupported extends Error("Refresh is not supported for PressAuto rallies") with LogicError

  private def fetchAndStore[F[_]: Async: Tracer: Network](rallyId: String)(using kind: RallyKind) =
    EmberClientBuilder
      .default[F]
      .withTimeout(Timeout)
      .withIdleConnectionTime(IdleTimeout)
      .build
      .map(Telemetry.tracedClient)
      .use { client =>
        (for
          info <- kind.rallyInfo(client, rallyId)
          results <- kind.rallyResults(client, rallyId)
          _ <- EitherT(Repo.deleteResultsAndRally(rallyId))
          _ <- EitherT(Repo.saveRallyInfo(rallyId, info))
          _ <- EitherT(Repo.saveRallyResults(rallyId, results))
        yield ()).value
      }

  def data[F[_]: Async: Tracer](kind: RallyKind, rallyId: String) =
    given RallyKind = kind
    for
      maybeRally <- EitherT(Repo.getRally(rallyId))
      rally <- EitherT.fromEither(maybeRally.fold(RallyNotStored.asLeft)(_.asRight))
      rallyResults <- EitherT(Repo.getRallyResults(rallyId))
    yield rallyData(rally, rallyResults)

  def refresh[F[_]: Async: Tracer: Network](kind: RallyKind, rallyId: String) =
    given RallyKind = kind
    for
      maybeRally <- EitherT(Repo.getRally(rallyId))
      needToFetch = maybeRally.fold(true)(_.retrievedAt.plusSeconds(60).isBefore(Instant.now))
      _ <- EitherT(if needToFetch then fetchAndStore(rallyId) else ().asRight.pure[F])
      storedData <- data(kind, rallyId)
    yield storedData

  def find[F[_]: Async: Tracer](
      kind: RallyKind,
      championship: String,
      year: Option[Int]
  ) =
    given RallyKind = kind
    Repo.findRallies[F](championship, year)

  def fresh[F[_]: Async: Tracer]() =
    (_: Unit) => Repo.freshRallies[F]()

  object Admin:
    def authLogic[F[_]: Async](usernamePassword: UsernamePassword): EitherT[F, Throwable, Unit] =
      usernamePassword match
        case UsernamePassword("admin", Some(password))
            if password.sha256hash == sys.env.getOrElse("ADMIN_PASS_HASH", "") =>
          EitherT.rightT(())
        case _ => EitherT.leftT(new Error("Unauthorized"))

    def refreshAll[F[_]: Async: Tracer: Network](): EitherT[F, Throwable, List[RefreshResult]] =
      for
        storedRallies <- EitherT(Db.selectRallies())
        refreshResults <- EitherT(
          storedRallies
            .map: (kind, rallyId) =>
              handleErrors(Logic.refresh(kind, rallyId).value)
                .map(rr => rr.fold(Some(_), _ => None))
                .map(RefreshResult(kind, rallyId, _))
            .sequence
            .map(_.asRight[Throwable])
        )
      yield refreshResults

    def deleteResultsAndRally[F[_]: Async: Tracer](kind: RallyKind, rallyId: String): EitherT[F, Throwable, Unit] =
      given RallyKind = kind
      EitherT(Repo.deleteResultsAndRally(rallyId)).map(_ => ())

def handleErrors[F[_]: Monad: Tracer, T](f: F[Either[Throwable, T]]) =
  for
    result <- f
    errorInfo <- result match
      case Left(error: Logic.LogicError) =>
        (error match
          case Logic.RallyNotStored      => RallyNotStored()
          case Logic.RallyInProgress     => RallyInProgress()
          case Logic.RefreshNotSupported => RefreshNotSupported()
        ).asLeft.pure[F]
      case Left(t) =>
        Tracer[F].currentSpanOrNoop.flatMap: span =>
          span.recordException(t) >> span.setStatus(StatusCode.Error) >> GenericError(t.toString).asLeft.pure[F]
      case Right(value) => value.asRight.pure[F]
  yield errorInfo

def httpServer[F[_]: Async: Network: Tracer: Spawn: Compression] =
  import cats.syntax.semigroupk.*

  for
    refreshShardedStreamAndLogic <- shardedLogic(NumRefreshShards)(
      Logic.refresh.tupled.andThen(_.value).andThen(handleErrors)
    ).toResource
    (refreshShardedStream, refreshShardedLogic) = refreshShardedStreamAndLogic
    _ <- refreshShardedStream.compile.drain.start.toResource
    server <- EmberServerBuilder
      .default[F]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromString(sys.env.getOrElse("RALLYEYE_SERVER_PORT", "8080")).get)
      .withIdleTimeout(Timeout)
      .withHttpApp {
        val interp = Http4sServerInterpreter[F]()
        val rally = Caching.cache(
          3.hours,
          isPublic = Left(CacheDirective.public),
          methodToSetOn = _ == Method.GET,
          statusToSetOn = _.isSuccess, {
            val endpoints =
              List(
                Endpoints.data.serverLogic(Logic.data.tupled.andThen(_.value).andThen(handleErrors)),
                Endpoints.refresh.serverLogic(refreshShardedLogic)
              )

            endpoints
              .map(interp.toRoutes)
              .reduce(_ <+> _)
          }
        )

        val admin = interp.toRoutes(
          Endpoints.Admin.refresh
            .serverSecurityLogic[Unit, F](
              Logic.Admin.authLogic
                .andThen(_.value)
                .andThen(handleErrors)
            )
            .serverLogic(_ => _ => handleErrors(Logic.Admin.refreshAll().value))
        ) <+> interp.toRoutes(
          Endpoints.Admin.delete
            .serverSecurityLogic(
              Logic.Admin.authLogic
                .andThen(_.value)
                .andThen(handleErrors)
            )
            .serverLogic(_ =>
              (rallyKind, rallyId) => handleErrors(Logic.Admin.deleteResultsAndRally(rallyKind, rallyId).value)
            )
        )

        val find =
          interp.toRoutes(Endpoints.find.serverLogic(Logic.find.tupled.andThen(_.value).andThen(handleErrors)))
        val fresh =
          interp.toRoutes(Endpoints.fresh.serverLogic(Logic.fresh().andThen(_.value).andThen(handleErrors)))

        Telemetry.tracedServer(
          GZip(
            CORS.policy.withAllowOriginAll(
              (rally <+> admin <+> find <+> fresh).orNotFound
            )
          )
        )
      }
      .build
  yield server
