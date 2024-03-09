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

import cats.effect.IO
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import rallyeye.shared.Endpoints
import rallyeye.shared.ErrorInfo
import rallyeye.shared.RallyData
import rallyeye.shared.RallyKind
import rallyeye.shared.RallySummary
import sttp.tapir.Endpoint
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.model.UsernamePassword

val Localhost = uri"http://localhost:8080"

trait ResultValidator[A]:
  extension (a: A) def validate(req: Request[IO]): Either[Throwable, String]

given ResultValidator[RallyData] with
  extension (rd: RallyData)
    def validate(req: Request[IO]) =
      if rd.allResults.size > 0 then Right(s"$req -> ${rd.allResults.size}")
      else Left(Error("No results"))

given ResultValidator[Unit] with
  extension (unit: Unit)
    def validate(req: Request[IO]) =
      Right(s"$req -> ()")

given ResultValidator[List[RallySummary]] with
  extension (rs: List[RallySummary])
    def validate(req: Request[IO]) =
      if rs.size > 0 then Right(s"$req -> $rs")
      else Left(Error("No results"))

def runRequest[Security, Req, Resp: ResultValidator](
    client: Client[IO],
    endpoint: Endpoint[Security, Req, ErrorInfo, Resp, Any],
    security: Security,
    req: Req
) =
  val (request, parseResponse) = Http4sClientInterpreter[IO]()
    .toSecureRequestThrowDecodeFailures(endpoint, Some(Localhost))(security)(req)
  client.run(request).use(parseResponse(_)).flatMap(printResults(request))

def printResults[Resp: ResultValidator](req: Request[IO])(resp: Either[ErrorInfo, Resp]) =
  resp
    .flatMap(_.validate(req))
    .fold(err => IO.raiseError(Error(err.toString)), IO.println)

val smokeRun = for
  _ <- rallyeye.storage.allMigrations
  server <- rallyeye.httpServer
  creds = UsernamePassword("admin", Some(sys.env.getOrElse("ADMIN_PASS", "")))
  _ <- server.use: s =>
    EmberClientBuilder
      .default[IO]
      .withTimeout(Timeout)
      .withIdleConnectionTime(IdleTimeout)
      .build
      .use { client =>
        for
          _ <- runRequest(client, Endpoints.PressAuto.data, (), "2023")
          _ <- runRequest(client, Endpoints.Rsf.refresh, (), "48272")
          _ <- runRequest(client, Endpoints.Rsf.data, (), "48272")
          _ <- runRequest(client, Endpoints.Ewrc.refresh, (), "80243-eko-acropolis-rally-greece-2023")
          _ <- runRequest(client, Endpoints.Ewrc.data, (), "80243-eko-acropolis-rally-greece-2023")
          _ <- runRequest(client, Endpoints.find, (), (RallyKind.Ewrc, "WRC", None))
          _ <- runRequest(client, Endpoints.Admin.refresh, creds, ())
          _ <- runRequest(client, Endpoints.Admin.Rsf.delete, creds, "48272")
          _ <- runRequest(client, Endpoints.Admin.Ewrc.delete, creds, "80243-eko-acropolis-rally-greece-2023")
        yield ()
      }
yield ()
