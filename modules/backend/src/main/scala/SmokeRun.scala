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

import java.nio.file.Files
import java.nio.file.Paths

import cats.effect.kernel.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.compression.Compression
import fs2.io.net.Network
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.Endpoints
import rallyeye.shared.ErrorInfo
import rallyeye.shared.GenericError
import rallyeye.shared.RallyData
import rallyeye.shared.RallyKind
import rallyeye.shared.RallySummary
import rallyeye.shared.RefreshResult
import rallyeye.storage.Db
import sttp.tapir.Endpoint
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.model.UsernamePassword

val Localhost = uri"http://localhost:8080"

trait ResultValidator[A]:
  extension (a: A) def validate[F[_]](req: Request[F]): Either[Throwable, String]

given ResultValidator[RallyData] with
  extension (rd: RallyData)
    def validate[F[_]](req: Request[F]) =
      if rd.allResults.size > 0 then Right(s"$req -> ${rd.allResults.size}")
      else Left(Error("No results"))

given ResultValidator[Unit] with
  extension (unit: Unit)
    def validate[F[_]](req: Request[F]) =
      Right(s"$req -> ()")

given ResultValidator[List[RallySummary]] with
  extension (rs: List[RallySummary])
    def validate[F[_]](req: Request[F]) =
      if rs.size > 0 then Right(s"$req -> $rs")
      else Left(Error("No results"))

given refreshResultValidator: ResultValidator[List[RefreshResult]] with
  extension (rs: List[RefreshResult])
    def validate[F[_]](req: Request[F]) =
      Right(s"$req -> $rs")

def runRequest[F[_]: Async, Security, Req, Resp: ResultValidator](
    client: Client[F],
    endpoint: Endpoint[Security, Req, ErrorInfo, Resp, Any],
    security: Security,
    req: Req
) =
  val (request, parseResponse) = Http4sClientInterpreter[F]()
    .toSecureRequestThrowDecodeFailures(endpoint, Some(Localhost))(security)(req)
  client.run(request).use(parseResponse(_)).map(validateResults(request))

def validateResults[F[_], Resp: ResultValidator](
    req: Request[F]
)(resp: Either[ErrorInfo, Resp]) =
  resp
    .flatMap(_.validate(req))
    .left
    .map:
      case t: Throwable => GenericError(t.getMessage)
      case e: ErrorInfo => e
    .map: e =>
      println(e); e

def smokeRun[F[_]: Async: Tracer: Meter: Network: Compression] =
  for
    _ <- Files.deleteIfExists(Paths.get(Db.file)).pure[F].toResource
    _ <- rallyeye.storage.allMigrations
    _ <- rallyeye.httpServer
    smokeTest = EmberClientBuilder
      .default[F]
      .withTimeout(Timeout)
      .withIdleConnectionTime(IdleTimeout)
      .build
      .map(Telemetry.tracedClient)
      .use { client =>
        val pressauto = (RallyKind.PressAuto, "2023")
        val rsf = (RallyKind.Rsf, "48272")
        val ewrc = (RallyKind.Ewrc, "80243-eko-acropolis-rally-greece-2023")
        val creds = UsernamePassword("admin", Some(sys.env.getOrElse("ADMIN_PASS", "")))
        for
          _ <- runRequest(client, Endpoints.data, (), pressauto)
          _ <- runRequest(client, Endpoints.refresh, (), rsf)
          _ <- runRequest(client, Endpoints.data, (), rsf)
          _ <- runRequest(client, Endpoints.refresh, (), ewrc)
          _ <- runRequest(client, Endpoints.data, (), ewrc)
          _ <- runRequest(client, Endpoints.find, (), (RallyKind.Ewrc, "WRC", None))
          _ <- runRequest(client, Endpoints.Admin.refresh, creds, ())
          _ <- runRequest(client, Endpoints.Admin.delete, creds, rsf)
          _ <- runRequest(client, Endpoints.Admin.delete, creds, ewrc)
        yield ()
      }
    _ <- smokeTest.toResource
  yield ()
