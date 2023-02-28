/*
 * Copyright 2023 github.com/2m/rallyeye-data/contributors
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

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}

val rallyEyeEndpoint = endpoint
  .in("rally" / path[Int])
  .out(header[String]("rally-name"))
  .out(streamTextBody(AkkaStreams)(CodecFormat.TextPlain()))

def rallyEyeRoute(using ActorSystem[Any]) =
  AkkaHttpServerInterpreter().toRoute(rallyEyeEndpoint.serverLogicSuccess { rallyId =>
    val nameRequest =
      HttpRequest(uri = {
        val uri = Uri("https://www.rallysimfans.hu/rbr/rally_online.php")
        uri.withQuery(Uri.Query("centerbox" -> "rally_results.php", "rally_id" -> rallyId.toString))
      })

    val rallyName = Http()
      .singleRequest(nameRequest)
      .flatMap { response =>
        response.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      .map { response =>
        val regexp = "Final standings for: (.*)<br>".r
        regexp.findFirstMatchIn(response.utf8String).get.group(1)
      }

    val resultsRequest =
      HttpRequest(uri = {
        val uri = Uri("https://www.rallysimfans.hu/rbr/csv_export_beta.php")
        uri.withQuery(Uri.Query("ngp_enable" -> "6", "rally_id" -> rallyId.toString))
      })

    val rallyResults = Http().singleRequest(resultsRequest)

    for
      name <- rallyName
      results <- rallyResults
    yield (name, results.entity.dataBytes)
  })

@main
def main() =
  given ActorSystem[Any] = ActorSystem(Behaviors.empty, "RallyEye")
  val binding = Http().newServerAt("localhost", 8080).bindFlow(rallyEyeRoute)
  Await.ready(Future.never, Duration.Inf)
