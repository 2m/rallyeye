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

import java.time.Instant

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.directives.CachingDirectives._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.config.ConfigFactory
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter

val rallyEyeEndpoint = endpoint
  .in("rally" / path[Int])
  .out(header[String]("rally-name"))
  .out(header[String]("results-retrieved-at"))
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

    for name <- rallyName
    yield (
      name,
      Instant.now().toEpochMilli.toString,
      Source.futureSource {
        for
          results <- rallyResults
          data <- results.entity.dataBytes.runFold(List.empty[ByteString])(_ :+ _)
        yield Source(data)
      }
    )
  })

@main
def main() =
  given ActorSystem[Any] = ActorSystem(
    Behaviors.empty,
    "RallyEye",
    ConfigFactory
      .parseString("""|akka.http.client.idle-timeout = 2m
                      |akka.http.server.idle-timeout = 2m
                      |akka.http.server.request-timeout = 2m
            """.stripMargin)
      .withFallback(ConfigFactory.defaultApplication())
  )

  val corsSettings = CorsSettings.defaultSettings.withExposedHeaders(List("rally-name", "results-retrieved-at"))
  val myCache = routeCache[Uri](summon[ActorSystem[Any]].toClassic)

  val binding = Http()
    .newServerAt("0.0.0.0", 8080)
    .bindFlow {
      cors(corsSettings) {
        cache(myCache, _.request.uri) {
          rallyEyeRoute
        }
      }
    }
    .onComplete {
      case Success(binding) =>
        binding.addToCoordinatedShutdown(5.seconds)
      case Failure(ex) =>
        summon[ActorSystem[Any]].terminate()
    }

  Await.ready(summon[ActorSystem[Any]].whenTerminated, Duration.Inf)
