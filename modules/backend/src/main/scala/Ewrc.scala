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

import scala.jdk.CollectionConverters.*
import scala.util.Try

import cats.data.EitherT
import cats.effect.IO
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

  val rallyEndpoint =
    endpoint
      .in("results")
      .in(path[String])
      .in("")
      .in(query[Option[Int]]("s"))
      .out(stringBody)

  def rallyStageResults(rallyId: String, stage: Option[Int]) =
    Http4sClientInterpreter[IO]()
      .toRequestThrowDecodeFailures(rallyEndpoint, Some(Ewrc))(rallyId, stage)

  def rallyName(client: Client[IO], rallyId: String): IO[Either[Error, String]] =
    val (request, parseResponse) = rallyStageResults(rallyId, None)
    for
      response <- client
        .run(request)
        .use(parseResponse(_))
        .map(_.left.map(_ => Error("Unable to parse Ewrc name response")))
      rallyName = response.map { body =>
        Scoup.parseHTML(body).select("html body main#main-section h3").first().text
      }
    yield rallyName

  def rallyResults(client: Client[IO], rallyId: String) =
    val (request, parseResponse) = rallyStageResults(rallyId, None)
    for
      response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error("Unable to parse Ewrc results response")))
      )
      stageIds = Scoup
        .parseHTML(response)
        .select(
          s"main#main-section div a.badge[href^=/results/$rallyId/?s][title^=SS]"
        )
        .iterator()
        .asScala
        .toList
        .map(_.attr("href").split("=").last.toInt)
      results <- EitherT(
        stageIds
          // .take(8)
          .traverse(stageResults(client, rallyId))
          .map(_.partitionMap(identity) match
            case (Nil, results) => Right(results.flatten)
            case (errors, _)    => Left(errors.head)
          )
      )
    yield results

  def stageResults(client: Client[IO], rallyId: String)(stageId: Int) =
    def getCountry(element: Element) =
      element.select("td img.flag-s").attr("src").split("/").last.split("\\.").head

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

    val (request, parseResponse) = rallyStageResults(rallyId, Some(stageId))
    for
      response <- client
        .run(request)
        .use(parseResponse(_))
        .map(_.left.map(_ => Error("Unable to parse Ewrc stage results response")))
      entries = response.map { body =>
        val document = Scoup.parseHTML(body)

        val (stageNumber, stageName) =
          getStageNumberAndName(document.select("main#main-section h5").first.textNodes.get(0).text)

        val stageResultTable = document
          .select("main#main-section div#stage-results table.results")
          .first()

        val stageCancelled =
          document.select("main#main-section div#stage-results span.badge-danger").text.contains("Stage cancelled")

        val infoPanels = document.select("main#main-section div.mt-3").iterator().asScala.toList
        val retired = infoPanels.find(_.text.contains("Retirement")).toList.flatMap { panel =>
          panel.select("tr").iterator().asScala.toList.map { retiredEntry =>
            val country = getCountry(retiredEntry)
            val driverCodriverName = retiredEntry.select("a").text
            val car = retiredEntry.select("td.retired-car").text
            Entry(
              stageNumber.refine,
              stageName,
              country,
              driverCodriverName,
              "",
              "",
              car,
              None,
              None,
              0,
              None,
              0,
              0,
              false,
              false,
              ""
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

        retired ++ stageResultTable.select("tr").iterator().asScala.toList.map { result =>
          val country = getCountry(result)

          val driverCodriverName = result.select("td.position-relative > a").text
          val car = result.select("td.position-relative > span").first.text

          val groupElement = result.select("td.px-1")
          groupElement.select("span").remove
          val group = groupElement.text

          val stageTimeElement = result.select("td.font-weight-bold.text-right").first
          stageTimeElement.select("span").remove
          val stageTime = getDurationMs(stageTimeElement.text)

          val superRally = result.select("td.position-relative > span").text.contains("[SR]")

          Entry(
            stageNumber.refine,
            stageName,
            country,
            driverCodriverName,
            "",
            group,
            car,
            None,
            None,
            (if !stageCancelled then stageTime else 0).refine,
            None,
            0,
            penalties.getOrElse(driverCodriverName, 0).refine,
            superRally,
            true,
            comments.getOrElse(driverCodriverName, ""),
            stageCancelled
          )
        }
      }
    yield entries
