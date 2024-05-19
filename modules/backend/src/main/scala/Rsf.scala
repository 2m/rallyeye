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

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate

import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.matching.Regex

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.implicits.*
import com.themillhousegroup.scoup.Scoup
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.data.csv
import fs2.data.csv.QuoteHandling
import fs2.data.csv.Row
import io.github.iltotore.iron.*
import org.http4s.client.Client
import org.http4s.implicits.*
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.MediaType
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter

object Rsf:
  val Rsf = uri"https://www.rallysimfans.hu"

  val ApplicationCsv = new CodecFormat:
    override val mediaType: MediaType = MediaType.unsafeApply(mainType = "application", subType = "csv")

  val rallyEndpoint =
    endpoint
      .in("rbr" / "rally_online.php")
      .in(query[String]("centerbox"))
      .in(query[String]("rally_id"))
      .out(stringBody)

  def resultsEndpoint[F[_]] =
    endpoint
      .in("rbr" / "csv_export_beta.php")
      .in(query[Int]("ngp_enable"))
      .in(query[String]("rally_id"))
      .out(streamTextBody(Fs2Streams[F])(ApplicationCsv, Some(StandardCharsets.UTF_8)))

  def rallyDetailsPage[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(rallyEndpoint, Some(Rsf))("rally_list_details.php", rallyId)

  def rallyResultsCsv[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(resultsEndpoint[F], Some(Rsf))(6, rallyId)

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
      distanceMeters.refineUnsafe,
      started.refineUnsafe,
      finished.refineUnsafe
    )

  def rallyResults[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, List[Entry]] =
    val (request, parseResponse) = rallyResultsCsv[F](rallyId)
    for
      response <- EitherT.rightT(client.stream(request))
      byteStream = response
        .evalMap(parseResponse)
        .flatMap(_.fold(_ => Stream.raiseError[F](Error("Unable to parse RSF results response")), identity))
      entryStream = byteStream.broadcastThrough(rallyInProgressPrefix[F], rallyResults[F])
      entryList <- EitherT(entryStream.compile.toList.map(_.asRight[Throwable]).handleError(_.asLeft[List[Entry]]))
    yield entryList

  def rallyInProgressPrefix[F[_]: Async]: Pipe[F, Byte, Entry] =
    val prefix = "The rally is not over yet."
    in =>
      in.take(prefix.length)
        .through(fs2.text.utf8.decode)
        .fold("")(_ + _)
        .flatMap:
          case `prefix` => Stream.raiseError[F](Logic.RallyInProgress)
          case _        => Stream.empty

  def rallyResults[F[_]: Async]: Pipe[F, Byte, Entry] =
    import fs2.data.text.utf8.*
    in =>
      in.through(csv.lowlevel.rows[F, Byte](separator = ';', quoteHandling = QuoteHandling.Literal))
        .through(csv.lowlevel.skipHeaders[F])
        .through(parseEntries[F])
        .through(trimAfterNotFinish[F])
        .through(trimAfterMissingStage[F])

  def parseEntries[F[_]]: Pipe[F, Row, Entry] =
    in =>
      in.map: row =>
        Entry(
          stageNumber = row.at(0).get.toInt.refineUnsafe,
          stageName = row.at(1).get.decodeHtmlUnicode,
          country = row.at(2).get,
          userName = row.at(3).get,
          realName = row.at(4).get.decodeHtmlUnicode,
          // until https://discord.com/channels/723091638951608320/792825986055798825/1114861057035489341 is fixed
          group = row.at(5).map(g => if g.isEmpty then "Rally 3" else g).get,
          car = row.at(6).get,
          split1Time = Try(BigDecimal(row.at(7).get)).toOption,
          split2Time = Try(BigDecimal(row.at(8).get)).toOption,
          stageTimeMs = row.at(9).get.toMs.refineUnsafe,
          finishRealtime = Try(Instant.parse(row.at(10).get.replace(" ", "T") + "+02:00")).toOption,
          // abs until https://discord.com/channels/@me/1176210913355898930/1210945143297810512 is fixed
          penaltyInsideStageMs = row.at(11).get.toMs.abs.refineUnsafe,
          penaltyOutsideStageMs = row.at(12).get.toMs.refineUnsafe,
          superRally = row.at(13).get == "1",
          finished = row.at(14).get == "F",
          comment = row.at(15).filter(_.nonEmpty)
        )

  def trimAfterNotFinish[F[_]]: Pipe[F, Entry, Entry] =
    _.scanChunks(List.empty[String]): (notFinised, chunk) =>
      chunk
        .foldLeft((notFinised, Chunk.empty[Entry])):
          case ((notFinished, entries), entry) =>
            // in some cases (when driver disconnects) it could continue a rally even after not
            // finishing a stage, so we need to filter out those entries
            (
              if !entry.finished then notFinished :+ entry.userName else notFinished,
              if notFinished.contains(entry.userName) then entries else entries ++ Chunk(entry)
            )

  def trimAfterMissingStage[F[_]]: Pipe[F, Entry, Entry] =
    _.scanChunks(Map.empty[String, Int]): (lastStages, chunk) =>
      chunk
        .foldLeft((lastStages, Chunk.empty[Entry])):
          case ((lastStages, entries), entry) =>
            // in some cases (when driver disconnects) it could continue a rally even after not
            // sending stage results at all, so we need to filter out those entries
            val lastStage = lastStages.get(entry.userName)
            val currentStage = entry.stageNumber
            val missingStage = lastStage.exists(_ + 1 != currentStage)
            (
              if missingStage then lastStages else lastStages.updated(entry.userName, currentStage),
              if missingStage then entries else entries ++ Chunk(entry)
            )
