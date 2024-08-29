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

import org.scalajs.dom
import rallyeye.shared.*
import sttp.client3.*
import sttp.tapir.DecodeResult
import sttp.tapir.client.sttp.SttpClientInterpreter

def fetch[Req, Resp](req: Req, endpoint: Endpoint[Req, Resp]) =
  val baseUri =
    if BuildInfo.isSnapshot then uri"http://${dom.window.location.hostname}:8080"
    else uri"https://rallyeye-data.fly.dev"

  val client = SttpClientInterpreter().toClient(endpoint, Some(baseUri), FetchBackend())
  val response = client(req)

  import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
  response.map {
    case DecodeResult.Value(r) => r
    case error                 => Left(GenericError(error.toString))
  }
