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

import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.chaining.*

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.syntax.all.*
import com.themillhousegroup.scoup.Scoup
import io.github.iltotore.iron.*
import org.http4s.client.Client
import org.http4s.implicits.uri
import org.jsoup.nodes.Element
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter

object Ewrc:
  val Ewrc = uri"https://www.ewrc-results.com/"

  val resultsEndpoint =
    endpoint
      .in("results")
      .in(path[String])
      .in("")
      .in(query[Option[Int]]("s"))
      .out(stringBody)

  val finalEndpoint =
    endpoint
      .in("final")
      .in(path[String])
      .in("")
      .out(stringBody)

  def resultsPage[F[_]: Async](rallyId: String, stage: Option[Int]) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(resultsEndpoint, Some(Ewrc))(rallyId, stage)

  def finalPage[F[_]: Async](rallyId: String) =
    Http4sClientInterpreter[F]()
      .toRequestThrowDecodeFailures(finalEndpoint, Some(Ewrc))(rallyId)

  def rallyInfo[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, RallyInfo] =
    val (request, parseResponse) = finalPage(rallyId)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Ewrc final page response")))
      )
      info <- EitherT.fromEither(Try(parseRallyInfo(response)).toEither)
    yield info

  def parseRallyInfo(finalPageBody: String): RallyInfo =
    val parsedPage = Scoup.parseHTML(finalPageBody)

    val nameWithPrefix = """(\d+)\. (.*)""".r
    val name = parsedPage.select("html body main#main-section h3").first.text match
      case nameWithPrefix(_, rallyName) => rallyName
      case name                         => name

    val topInfo = parsedPage.select("html body main#main-section div.top-info").first.text
    val topInfoParts = topInfo.split("•").toList.map(_.trim)
    val (start, end) = topInfoParts.head match
      case s"$startDay. $startMonth. – $endDate. $endMonth. $year" =>
        val yearValue = year.takeWhile(_.isDigit).toInt
        (
          LocalDate.of(yearValue, startMonth.toInt, startDay.toInt),
          LocalDate.of(yearValue, endMonth.toInt, endDate.toInt)
        )
      case s"$day. $month. $year, $organizer" =>
        val date = LocalDate.of(year.toInt, month.toInt, day.toInt)
        (date, date)

    val distanceRegexp = """[^\d]*(\d+)\.(\d+) km.*""".r
    val distanceMeters = topInfoParts
      .find(_.contains(" km"))
      .getOrElse(throw Error("unable to find top info part with rally distance"))
      .split("cancelled")
      .head match
      case distanceRegexp(kilometers, decimeters) =>
        kilometers.toInt * 1000 + (decimeters.toInt * 10)
      case distancePart => throw Error(s"Unable to parse rally distance from [$distancePart}]")

    val topSections = parsedPage.select("html body main#main-section div.top-sections").first.text
    val championship = topSections.split("•").toList.map(_.split("#").head.trim)

    val finishedElements = parsedPage
      .select("html body main#main-section div.text-center.text-primary.font-weight-bold")
      .iterator()
      .asScala
      .toList
    val finished = finishedElements.find(_.text.contains("finished")) match
      case Some(finishedElement) =>
        val finishedRegexp = """finished: (\d+) .*""".r
        finishedElement.text match
          case finishedRegexp(finishedCount) => finishedCount.toInt
          case _ => throw new Error(s"Unable to parse finished count from [${finishedElement.text}]")
      case None => 0 // rally still in progress

    val retirementsElements = parsedPage
      .select("html body main#main-section div.final-results table.results tbody h4.text-center.mt-3")
      .iterator()
      .asScala
      .toList
    val retirements = retirementsElements.find(_.text.contains("Retirements")) match
      case Some(retirementsElement) =>
        val retirementsRegexp = """(\d+) .*""".r
        retirementsElement.siblingElements.first.text match
          case retirementsRegexp(retirementsCount) => retirementsCount.toInt
          case _                                   => 0 // no retirements specified in the page
      case None => 0 // no retirements yet in the rally

    RallyInfo(
      name,
      championship,
      start,
      end,
      distanceMeters.refineUnsafe,
      (finished + retirements).refineUnsafe,
      finished.refineUnsafe
    )

  case class Retired(group: String)

  def retiredNumberGroup[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, Map[String, Retired]] =
    val (request, parseResponse) = finalPage(rallyId)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Ewrc final response")))
      )
      retiredDrivers <- EitherT.fromEither(Try(parseRetiredNumberGroup(response)).toEither)
    yield retiredDrivers

  private def parseRetiredNumberGroup(finalPageBody: String) =
    Scoup
      .parseHTML(finalPageBody)
      .select(".final-results-stage")
      .iterator()
      .asScala
      .toList
      .map(_.parent)
      .map: retiredRow =>
        val number = retiredRow.select(".final-results-number").text
        val group = retiredRow.select(".final-results-cat").text
        number -> Retired(group)
      .toMap

  private def parseStageIds(finalPageBody: String, rallyId: String) =
    Scoup
      .parseHTML(finalPageBody)
      .select(
        s"main#main-section div a.badge[href^=/results/$rallyId/?s][title^=SS]"
      )
      .iterator()
      .asScala
      .toList
      .map(_.attr("href").split("=").last.toInt)

  def rallyResults[F[_]: Async](client: Client[F], rallyId: String): EitherT[F, Throwable, List[Entry]] =
    val (request, parseResponse) = resultsPage(rallyId, None)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Ewrc results response")))
      )
      retiredDrivers <- retiredNumberGroup(client, rallyId)
      stageIds <- EitherT.fromEither(Try(parseStageIds(response, rallyId)).toEither)
      results <-
        stageIds
          .traverse(stageResults(client, rallyId, retiredDrivers))
          .map(_.flatten)
    yield results

  def stageResults[F[_]: Async](client: Client[F], rallyId: String, retiredDrivers: Map[String, Retired])(
      stageId: Int
  ) =
    val (request, parseResponse) = resultsPage(rallyId, Some(stageId))
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Ewrc stage results response")))
      )
      entries <- EitherT.fromEither(Try(parseStageResults(response, retiredDrivers)).toEither)
    yield entries

  private def parseStageResults(resultsPageBody: String, retiredDrivers: Map[String, Retired]) =
    def getCountry(element: Element) =
      element.select("td img.flag-s").attr("src").split("/").last.split("\\.").head match
        case "uk"           => "united kingdom"
        case "saudi_arabia" => "saudi arabia"
        case "costa_rica"   => "costa rica"
        case "nederland"    => "netherlands"
        case "jar"          => "south africa"
        case "newzealand"   => "new zealand"
        case c              => c

    def getDurationMs(s: String) =
      s.split(":").toList match
        case hours :: minutes :: secondsAndTenths :: Nil =>
          (hours.toInt * 3600 + minutes.toInt * 60) * 1000 + secondsAndTenths.toMs
        case minutes :: secondsAndTenths :: Nil =>
          minutes.toInt * 60 * 1000 + secondsAndTenths.toMs
        case secondsAndTenths :: Nil =>
          secondsAndTenths.toMs
        case time => throw Error(s"Unable to parse stage time from $time")

    def getStageNumberAndName(s: String) =
      Try:
        s match
          case s"SS$stageNumber $stageName - $_ km" => (stageNumber.toInt, stageName)
          case s"SS$stageNumber $stageName"         => (stageNumber.toInt, stageName)
      .fold(_ => throw Error(s"Unable to parse stage number and name from [$s]"), identity)

    val document = Scoup.parseHTML(resultsPageBody)

    val (stageNumber, stageName) =
      getStageNumberAndName(document.select("main#main-section h5").first.textNodes.get(0).text)

    val stageResultTable = document
      .select("main#main-section div#stage-results table.results")
      .first()
      .pipe(Option.apply)
      .toList
      .flatMap: table =>
        table
          .select("tr")
          .iterator()
          .asScala
          .toList

    val overallResultTable = document
      .select("main#main-section div#stage-results table.results")
      .last()
      .pipe(Option.apply)
      .toList
      .flatMap: table =>
        table
          .select("tr")
          .iterator()
          .asScala
          .toList

    val stageCancelled =
      document.select("main#main-section div#stage-results span.badge-danger").text.contains("Stage cancelled")

    val infoPanels = document.select("main#main-section div.mt-3").iterator().asScala.toList
    val retired = infoPanels.find(_.text.contains("Retirement")).toList.flatMap { panel =>
      panel.select("tr").iterator().asScala.toList.map { retiredEntry =>
        val country = getCountry(retiredEntry)
        val driverCodriverName = retiredEntry.select("a").text
        val car = retiredEntry.select("td.retired-car").text
        val entryNumber = retiredEntry.select("td.font-weight-bold.text-primary").text
        Entry(
          stageNumber.refineUnsafe,
          stageName,
          country,
          driverCodriverName,
          "",
          retiredDrivers(entryNumber).group,
          car,
          None,
          None,
          0,
          None,
          0,
          0,
          false,
          false,
          None
        )
      }
    }

    val comments = infoPanels
      .find(_.select("h6").text.contains("Info"))
      .toList
      .flatMap { panel =>
        panel
          .select("div.info-inc-info.mt-1")
          .iterator()
          .asScala
          .toList
          .map { infoRow =>
            infoRow.select("i.fa-comment-lines").iterator().asScala.toList.headOption.fold(None) { _ =>
              val driverCodriverName = infoRow.select("a").text
              val comment = infoRow.select("div.lh-130.p-1").text.drop(1).dropRight(1)
              Some(driverCodriverName -> comment)
            }
          }
          .flatten
      }
      .toMap

    val penalties = infoPanels
      .find(_.select("h6").text.contains("Penalty"))
      .toList
      .flatMap { panel =>
        panel
          .select("tr")
          .iterator()
          .asScala
          .toList
          .map { penaltyRow =>
            val driverCodriverName = penaltyRow.select("a").text
            val penalty = getDurationMs(penaltyRow.select("td.text-danger.font-weight-bold").text.split(" ").head)
            driverCodriverName -> penalty
          }
      }
      .toMap

    val driversInOverallTable = overallResultTable.map: result =>
      result.select("td span.font-weight-bold.text-primary").text

    retired ++ stageResultTable
      .filter { result =>
        // consider only those drivers that are still in the overall results
        // some drivers do not start a stage, but then continue in the next loop
        // in such cases they are sometimes not listed in the retired drivers list
        // but continue to be present in the stage times table
        val entryNumber = result.select("td.text-left span.font-weight-bold.text-primary").text

        // if the stage was cancelled, there is only a stage results table, so no filtering
        driversInOverallTable.contains(entryNumber) || stageCancelled
      }
      .map { result =>
        val country = getCountry(result)

        val driverCodriverName = result.select("td.position-relative > a").text
        val car = result.select("td.position-relative > span").first.text

        val groupElement = result.select("td.px-1")
        groupElement.select(".badge-x").remove: @nowarn("msg=discarded expression")
        val group = groupElement.text

        val stageTimeElement = result.select("td.font-weight-bold.text-right").first
        val nominalTime = stageTimeElement.text.contains("[N]")
        stageTimeElement.select("span").remove: @nowarn("msg=discarded expression")
        val stageTime = getDurationMs(stageTimeElement.text)

        val superRally = result.select("td.position-relative > span").text.contains("[SR]")

        Entry(
          stageNumber.refineUnsafe,
          stageName,
          country,
          driverCodriverName,
          "",
          group,
          car,
          None,
          None,
          (if !stageCancelled then stageTime else 0).refineUnsafe,
          None,
          0,
          penalties.getOrElse(driverCodriverName, 0).refineUnsafe,
          superRally,
          true,
          comments.get(driverCodriverName),
          nominalTime || stageCancelled
        )
      }
