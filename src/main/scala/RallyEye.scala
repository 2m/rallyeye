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

import sttp.client3._
import sttp.tapir._
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}
import sttp.tapir.server.netty.NettyOptions

val rallyEyeData = endpoint.in("rally" / path[Int]).out(stringBody).out(header[String]("rally-name"))
val backend = HttpClientFutureBackend(customEncodingHandler = { case (s, "UTF-8") => s })

@main
def main() =
  val binding =
    NettyFutureServer(
      NettyFutureServerOptions.customiseInterceptors
        .corsInterceptor(CORSInterceptor.customOrThrow(CORSConfig.default.exposeAllHeaders))
        .options
        .nettyOptions(NettyOptions.default.host("0.0.0.0"))
    )
      .addEndpoint(rallyEyeData.serverLogic { rallyId =>
        val nameRequest = quickRequest
          .get(
            uri"https://www.rallysimfans.hu/rbr/rally_online.php?centerbox=rally_results.php"
              .addParam("rally_id", rallyId.toString)
          )
          .send(backend)
          .map { response =>
            val regexp = "Final standings for: (.*)<br>".r
            regexp.findFirstMatchIn(response.body).get.group(1)
          }

        val resultsRequest = quickRequest
          .get(
            uri"https://www.rallysimfans.hu/rbr/csv_export_beta.php?ngp_enable=6".addParam("rally_id", rallyId.toString)
          )
          .send(backend)
          .map(_.body)

        for
          name <- nameRequest
          results <- resultsRequest
        yield Right(results, name)
      })
      .start()
      .map(println)

  Await.ready(binding, Duration.Inf)
