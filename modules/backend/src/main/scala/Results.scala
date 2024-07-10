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

import scala.collection.MapView
import scala.util.chaining.*

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.syntax.all.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import org.http4s.client.Client
import rallyeye.Logic.RefreshNotSupported
import rallyeye.shared.*
import rallyeye.storage.Rally

case class RallyInfo(
    name: String,
    championship: List[String],
    start: LocalDate,
    end: LocalDate,
    distanceMeters: Int :| Greater[0],
    started: Int :| GreaterEqual[0],
    finished: Int :| GreaterEqual[0]
)

extension (kind: RallyKind)
  def link(rally: Rally) =
    rally.kind match
      case RallyKind.Rsf =>
        s"https://www.rallysimfans.hu/rbr/rally_online.php?centerbox=rally_list_details.php&rally_id=${rally.externalId}"
      case RallyKind.PressAuto if rally.externalId == "2023" =>
        s"https://raceadmin.eu/pr${rally.externalId}/pr${rally.externalId}/results/overall/all"
      case RallyKind.PressAuto =>
        val year = rally.externalId.drop(2)
        s"https://raceadmin.eu/pr$year/pr$year/results/all/overall"
      case RallyKind.Ewrc =>
        s"https://www.ewrc-results.com/results/${rally.externalId}/"

  def rallyInfo[F[_]: Async](client: Client[F], rallyId: String) =
    kind match
      case RallyKind.Rsf       => rallyeye.Rsf.rallyInfo(client, rallyId)
      case RallyKind.Ewrc      => rallyeye.Ewrc.rallyInfo(client, rallyId)
      case RallyKind.PressAuto => EitherT(RefreshNotSupported.asLeft.pure[F])

  def rallyResults[F[_]: Async](client: Client[F], rallyId: String) =
    kind match
      case RallyKind.Rsf       => rallyeye.Rsf.rallyResults(client, rallyId)
      case RallyKind.Ewrc      => rallyeye.Ewrc.rallyResults(client, rallyId)
      case RallyKind.PressAuto => EitherT(RefreshNotSupported.asLeft.pure[F])

case class Entry(
    stageNumber: Int :| Greater[0],
    stageName: String,
    country: String,
    userName: String,
    realName: String,
    group: List[String],
    car: String,
    split1Time: Option[BigDecimal] = None,
    split2Time: Option[BigDecimal] = None,
    stageTimeMs: Int :| GreaterEqual[0],
    finishRealtime: Option[Instant] = None,
    penaltyInsideStageMs: Int :| GreaterEqual[0],
    penaltyOutsideStageMs: Int :| GreaterEqual[0],
    superRally: Boolean,
    finished: Boolean,
    comment: Option[String],
    nominal: Boolean = false
)

case class TimeResult(
    stageNumber: Int,
    stageName: String,
    country: String,
    userName: String,
    realName: String,
    stageTimeMs: Int,
    overallTimeMs: Int,
    penaltyInsideStageMs: Int,
    penaltyOutsideStageMs: Int,
    superRally: Boolean,
    finished: Boolean,
    comment: Option[String],
    nominal: Boolean
)

case class PositionResult(
    stageNumber: Int,
    country: String,
    userName: String,
    realName: String,
    stagePosition: Int,
    overallPosition: Int,
    stageTimeMs: Int,
    overallTimeMs: Int,
    penaltyInsideStageMs: Int,
    penaltyOutsideStageMs: Int,
    superRally: Boolean,
    rallyFinished: Boolean,
    comment: Option[String],
    nominal: Boolean
)

def stages(entries: List[Entry]) =
  entries.map(e => Stage(e.stageNumber, e.stageName)).distinct.sortBy(_.number)

def results(entries: List[Entry]) =
  val withOverall = entries
    .groupBy(_.userName)
    .view
    .mapValues { results =>
      val overallTimes =
        results.scanLeft(0)((sofar, entry) => sofar + entry.stageTimeMs + entry.penaltyOutsideStageMs)
      results
        .zip(overallTimes.drop(1))
        .map((e, overall) =>
          TimeResult(
            e.stageNumber,
            e.stageName,
            e.country,
            e.userName,
            e.realName,
            e.stageTimeMs,
            overall,
            e.penaltyInsideStageMs,
            e.penaltyOutsideStageMs,
            e.superRally,
            e.finished,
            e.comment,
            e.nominal
          )
        )
    }
    .values
    .flatten

  val retired = withOverall.filterNot(_.finished).map(_.userName).toSet

  withOverall.groupBy(r => Stage(r.stageNumber, r.stageName)).view.mapValues { results =>
    val stageResults = results.toList.filter(_.finished).sortBy(_.stageTimeMs)
    val overallResults = results.toList.filter(_.finished).sortBy(_.overallTimeMs)
    overallResults.zipWithIndex.map { (result, overall) =>
      PositionResult(
        result.stageNumber,
        result.country,
        result.userName,
        result.realName,
        stageResults.indexOf(result) + 1,
        overall + 1,
        result.stageTimeMs,
        result.overallTimeMs,
        result.penaltyInsideStageMs,
        result.penaltyOutsideStageMs,
        result.superRally,
        !retired.contains(result.userName),
        result.comment,
        result.nominal
      )
    }
  }

def drivers(results: MapView[Stage, List[PositionResult]]) =
  results
    .flatMap((stage, positionResults) =>
      positionResults.map(r =>
        DriverResults(
          Driver(r.country, r.userName, r.realName),
          List(
            DriverResult(
              stage.number,
              r.stagePosition,
              r.overallPosition,
              r.stageTimeMs,
              r.overallTimeMs,
              r.penaltyInsideStageMs,
              r.penaltyOutsideStageMs,
              r.superRally,
              r.rallyFinished,
              r.comment,
              r.nominal
            )
          )
        )
      )
    )
    .groupBy(_.driver.userName)
    .map((_, results) =>
      DriverResults(
        results.head.driver,
        results.flatMap(_.results).toList.sortBy(_.stageNumber)
      )
    )
    .toList
    .sortBy(_.driver.userName)

def rallyData(rally: Rally, entries: List[Entry]) =
  val groupResults =
    entries.flatMap(entry => entry.group.map(g => entry.copy(group = List(g)))).groupBy(_.group).map {
      case (group, entries) => GroupResults(group.head, results(entries) pipe drivers)
    }
  val carResults =
    entries.flatMap(entry => entry.group.map(g => entry.copy(group = List(g)))).groupBy(e => (e.group, e.car)).map {
      case ((group, car), entries) => CarResults(car, group.head, results(entries) pipe drivers)
    }

  RallyData(
    rally.externalId,
    rally.name,
    rally.kind.link(rally),
    rally.retrievedAt,
    stages(entries),
    results(entries) pipe drivers,
    groupResults.toList,
    carResults.toList
  )
