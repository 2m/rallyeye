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
import java.time.LocalDate

import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.matching.Regex

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.implicits.*
import com.themillhousegroup.scoup.Scoup
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

  def rallyDetailsPage[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(rallyEndpoint, Some(Rsf))("rally_list_details.php", rallyId)

  def rallyResultsCsv[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(resultsEndpoint, Some(Rsf))(6, rallyId)

  def rallyInfo[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, RallyInfo] =
    val (request, parseResponse) = rallyDetailsPage(rallyId)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse RSF name response")))
      )
      info <- EitherT.fromEither(Try(parseRallyInfo(response)).toEither)
    yield info

  def parseRallyInfo(detailsPageBody: String): RallyInfo =
    val parsedPage = Scoup.parseHTML(detailsPageBody)

    val name =
      parsedPage.select("html body div#page-wrap table tbody table tbody table tbody tr.fejlec td > b").first.text

    val championshipLinks = parsedPage
      .select("html body div#page-wrap table tbody table tbody table tbody tr.fejlec td div.point")
      .iterator()
      .asScala
      .toList
    val championship = championshipLinks.find(_.attr("onclick").contains("b_rally_list_details")) match
      case Some(championshipLink) => List(championshipLink.text)
      case None                   => List.empty

    val infoTable = parsedPage
      .select("html body div#page-wrap table tbody table tbody table tbody tr.paros td")
      .iterator()
      .asScala
      .toList

    val distanceMeters = infoTable.find(_.text.contains("Total Distance Rally")) match
      case Some(distanceElement) =>
        distanceElement.siblingElements.first.text match
          case s"$kilometers.$hectometers km" => kilometers.toInt * 1000 + (hectometers.toInt * 100)
          case _ => throw new Error(s"Unable to parse distance from [${distanceElement.siblingElements.first.text}]")
      case None => throw new Error(s"Unable to find distance element in [$infoTable]")

    val (started, finished) = infoTable.find(_.text.contains("Started/Finished")) match
      case Some(startedFinishedElement) =>
        startedFinishedElement.siblingElements.first.text match
          case s"$started / $finished" => (started.toInt, finished.toInt)
          case _ =>
            throw new Error(
              s"Unable to parse started, finished from [${startedFinishedElement.siblingElements.first.text}]"
            )
      case None => throw new Error(s"Unable to find started, finished element in [$infoTable]")

    val firstDateRegexp = """(\d+)-(\d+)-(\d+).*""".r
    val lastDateRegexp = """.* (\d+)-(\d+)-(\d+) .*""".r
    def parseDate(text: String, regexp: Regex) =
      text match
        case regexp(year, month, day) => LocalDate.of(year.toInt, month.toInt, day.toInt)
        case _                        => throw new Error(s"Unable to parse start date from [$text]")

    val (start, end) = infoTable.filter(_.text.contains("Leg ")) match
      case first +: _ :+ last =>
        (
          parseDate(first.siblingElements.first.text, firstDateRegexp),
          parseDate(last.siblingElements.first.text, lastDateRegexp)
        )
      case single :: Nil =>
        val singleLeg = single.siblingElements.first.text
        (parseDate(singleLeg, firstDateRegexp), parseDate(singleLeg, lastDateRegexp))
      case _ => throw new Error(s"Unable to find leg elements in [$infoTable]")

    RallyInfo(
      name,
      championship,
      start,
      end,
      distanceMeters.refine,
      started.refine,
      finished.refine
    )

  def rallyResults[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, List[Entry]] =
    val (request, parseResponse) = rallyResultsCsv(rallyId)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse RSF results response")))
      )
      result <- EitherT.fromEither(response match
        case r if r.contains("The rally is not over yet.") => Left(Logic.RallyInProgress)
        case r                                             => Try(parseResults(r)).toEither
      )
    yield result

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
          penalty.toMs.abs.refine, // abs until https://discord.com/channels/@me/1176210913355898930/1210945143297810512 is fixed
          servicePenalty.toMs.refine,
          superRally == "1",
          finished == "F",
          comment
        )
      case _ => ???
    }
