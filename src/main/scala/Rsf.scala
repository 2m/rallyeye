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

import cats.effect.IO
import org.http4s.implicits._
import sttp.tapir._
import sttp.tapir.client.http4s.Http4sClientInterpreter

object Rsf:
  val Rsf = uri"https://www.rallysimfans.hu"

  val rallyEndpoint =
    endpoint
      .in("rbr" / "rally_online.php")
      .in(query[String]("centerbox"))
      .in(query[Int]("rally_id"))
      .out(stringBody)

  val resultsEndpoint =
    endpoint
      .in("rbr" / "csv_export_beta.php")
      .in(query[Int]("ngp_enable"))
      .in(query[Int]("rally_id"))
      .out(stringBody)

  def rallyName(rallyId: Int) =
    Http4sClientInterpreter[IO]()
      .toRequestThrowDecodeFailures(rallyEndpoint, Some(Rsf))("rally_results.php", rallyId)

  def rallyResults(rallyId: Int) =
    Http4sClientInterpreter[IO]()
      .toRequestThrowDecodeFailures(resultsEndpoint, Some(Rsf))(6, rallyId)
