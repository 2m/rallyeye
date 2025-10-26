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

import java.time.LocalDate

import scala.jdk.CollectionConverters.*
import scala.util.Try

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.syntax.all.*
import com.themillhousegroup.scoup.Scoup
import io.github.iltotore.iron.*
import org.http4s.client.Client
import org.http4s.implicits.uri
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter

object Autorally:
  val Autorally = uri"https://autorally.lv"

  val infoEndpoint = endpoint.in(query[String]("r")).out(stringBody)

  val resultsEndpoint = endpoint
    .in("belgium" / "files" / "r504" / "results_r.php")
    .in(query[String]("f"))
    .in(query[String]("gl_lang"))
    .in(query[String]("gl_rally"))
    .out(stringBody)

  def infoPage[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(infoEndpoint, Some(Autorally))(rallyId)

  def resultsPage[F[_]: Async](rallyId: String, stage: Option[Int]) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(resultsEndpoint, Some(Autorally))(stage.getOrElse(1).toString, "ENG", rallyId)

  def rallyInfo[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, RallyInfo] =
    val (request, parseResponse) = infoPage(rallyId)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Autorally info page response")))
      )
      info <- EitherT.fromEither(Try(parseRallyInfo(response)).toEither)
    yield info

  def parseRallyInfo(infoPageBody: String): RallyInfo =
    val parsedPage = Scoup.parseHTML(infoPageBody)

    val dates = parsedPage.select(".datetill").text()
    val (start, end) = dates match
      case s"$startDay.$startMonth-$endDay.$endMonth.$year" =>
        (
          LocalDate.of(year.toInt, startMonth.toInt, startDay.toInt),
          LocalDate.of(year.toInt, endMonth.toInt, endDay.toInt)
        )

    RallyInfo(
      s"Press Auto ${end.getYear()}",
      List("Press Auto"),
      start,
      end,
      1,
      82,
      80
    )

  def rallyResults[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, List[Entry]] =
    val (request, parseResponse) = resultsPage(rallyId, None)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse AutoRally results response")))
      )
      stageIds <- EitherT.fromEither(Try(parseStageCount(response)).toEither)
      results <-
        (1 to stageIds).toList
          .traverse(stageResults(client, rallyId))
          .map(_.flatten)
    yield results

  def parseStageCount(resultsPageBody: String): Int =
    val parsedPage = Scoup.parseHTML(resultsPageBody)
    parsedPage.select("a.ssbutton").iterator().asScala.toList.last.text().toInt

  def stageResults[F[_]: Async](client: Client[F], rallyId: String)(stageId: Int): EitherT[F, Throwable, List[Entry]] =
    val (request, parseResponse) = resultsPage(rallyId, Some(stageId))
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse AutoRally stage results response")))
      )
      entries <- EitherT.fromEither(Try(parseStageResults(response, stageId)).toEither)
    yield entries

  def parseStageResults(resultsPageBody: String, stageNumber: Int): List[Entry] =
    val document = Scoup.parseHTML(resultsPageBody)

    val stageName = document.select("span.stage-info").text().takeWhile(_ != ',')

    document
      .select("span.col1 ul.stage-times li")
      .iterator()
      .asScala
      .toList
      .map: stage =>
        val country = stage.select("span.flag img").attr("src").split("/").last.split("\\.").head
        val driverCodriverName = stage.select("span.names strong").text()
        val (group, car) = stage.select("span.names p").text() match
          case s"$car ($group)" => (group.trim(), car)

        val stageTimeMs = Ewrc.getDurationMs(stage.select("span.time strong").text())
        val penaltyTimeMs = stage
          .select("span.penaltytime")
          .iterator()
          .asScala
          .toList
          .map(_.text())
          .map:
            case "Sch."  => 0
            case penalty => Ewrc.getDurationMs(penalty)
          .sum

        Entry(
          stageNumber.refineUnsafe,
          stageName,
          country,
          driverCodriverName,
          "",
          List(group),
          car,
          None,
          None,
          stageTimeMs.refineUnsafe,
          None,
          0,
          penaltyTimeMs.refineUnsafe,
          false,
          true,
          None
        )
