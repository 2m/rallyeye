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
import sttp.tapir.Endpoint
import sttp.tapir.client.http4s.Http4sClientInterpreter

val Localhost = uri"http://localhost:8080"

def runRequest(client: Client[IO], endpoint: Endpoint[Unit, String, ErrorInfo, RallyData, Any], req: String) =
  val (request, parseResponse) = Http4sClientInterpreter[IO]()
    .toRequestThrowDecodeFailures(endpoint, Some(Localhost))(req)
  client.run(request).use(parseResponse(_)).flatMap(printResults(request))

def printResults(req: Request[IO])(resp: Either[ErrorInfo, RallyData]) =
  resp
    .flatMap: rd =>
      if rd.allResults.size > 0 then Right(s"$req -> ${rd.allResults.size}")
      else Left(Error("No results"))
    .fold(err => IO.raiseError(Error(err.toString)), IO.println)

val smokeRun = for
  _ <- rallyeye.storage.allMigrations
  server <- rallyeye.httpServer
  _ <- server.use: s =>
    EmberClientBuilder
      .default[IO]
      .withTimeout(Timeout)
      .withIdleConnectionTime(IdleTimeout)
      .build
      .use { client =>
        for
          _ <- runRequest(client, Endpoints.PressAuto.data, "2023")
          _ <- runRequest(client, Endpoints.Rsf.refresh, "48272")
          _ <- runRequest(client, Endpoints.Rsf.data, "48272")
          _ <- runRequest(client, Endpoints.Ewrc.refresh, "80243-eko-acropolis-rally-greece-2023")
          _ <- runRequest(client, Endpoints.Ewrc.data, "80243-eko-acropolis-rally-greece-2023")
        yield ()
      }
yield ()
