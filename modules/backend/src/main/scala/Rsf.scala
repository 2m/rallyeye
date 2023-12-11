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

import scala.util.Try

import cats.effect.IO
import io.github.iltotore.iron.*
import org.http4s.client.Client
import org.http4s.implicits.*
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter

object Rsf:
  val Rsf = uri"https://www.rallysimfans.hu"

  val rallyEndpoint =
    endpoint
      .in("rbr" / "rally_online.php")
      .in(query[String]("centerbox"))
      .in(query[String]("rally_id"))
      .out(stringBody)

  val resultsEndpoint =
    endpoint
      .in("rbr" / "csv_export_beta.php")
      .in(query[Int]("ngp_enable"))
      .in(query[String]("rally_id"))
      .out(stringBody)

  def rallyName(rallyId: String) =
    Http4sClientInterpreter[IO]()
      .toRequestThrowDecodeFailures(rallyEndpoint, Some(Rsf))("rally_results.php", rallyId)

  def rallyResults(rallyId: String) =
    Http4sClientInterpreter[IO]()
      .toRequestThrowDecodeFailures(resultsEndpoint, Some(Rsf))(6, rallyId)

  def rallyName(client: Client[IO], rallyId: String): IO[Either[Error, String]] =
    val (request, parseResponse) = rallyName(rallyId)
    for
      response <- client
        .run(request)
        .use(parseResponse(_))
        .map(_.left.map(_ => Error("Unable to parse RSF name response")))
      rallyName = response.map { body =>
        val regexp = "Final standings for: (.*)<table".r
        regexp.findFirstMatchIn(body).get.group(1)
      }
    yield rallyName

  def rallyResults(client: Client[IO], rallyId: String): IO[Either[Error, List[Entry]]] =
    val (request, parseResponse) = rallyResults(rallyId)
    for
      response <- client
        .run(request)
        .use(parseResponse(_))
        .map(_.left.map(_ => Error("Unable to parse RSF results response")))
      entries = response.map(parseResults)
    yield entries

  def parseResults(csv: String) =
    val (header :: data) = csv.split('\n').toList: @unchecked
    data.map(_.split(";", -1).toList).map {
      case stageNumber :: stageName :: country :: userName :: realName :: group :: car :: time1 :: time2 :: time3 :: finishRealtime :: penalty :: servicePenalty :: superRally :: finished :: comment :: Nil =>
        Entry(
          stageNumber.toInt.refine,
          stageName,
          country,
          userName,
          realName.decodeHtmlUnicode,
          // until https://discord.com/channels/723091638951608320/792825986055798825/1114861057035489341 is fixed
          if group.isEmpty then "Rally 3" else group,
          car,
          Try(BigDecimal(time1)).toOption,
          Try(BigDecimal(time2)).toOption,
          time3.toMs.refine,
          Try(Instant.parse(finishRealtime.replace(" ", "T") + "+02:00")).toOption,
          penalty.toMs.refine,
          servicePenalty.toMs.refine,
          superRally == "1",
          finished == "F",
          comment
        )
      case _ => ???
    }
