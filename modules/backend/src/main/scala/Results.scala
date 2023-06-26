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

import scala.collection.MapView
import scala.util.Try
import scala.util.chaining._

import rallyeye.shared._

case class Entry(
    stageNumber: Int,
    stageName: String,
    userName: String,
    group: String,
    car: String,
    stageTime: BigDecimal,
    superRally: Boolean,
    finished: Boolean,
    comment: String,
    nominal: Boolean = false
)

case class TimeResult(
    stageNumber: Int,
    stageName: String,
    userName: String,
    stageTime: BigDecimal,
    overallTime: BigDecimal,
    superRally: Boolean,
    finished: Boolean,
    comment: String
)

def parse(csv: String) =
  val (header :: data) = csv.split('\n').toList: @unchecked
  data.map(_.split(";", -1).toList).map {
    case stageNumber :: stageName :: country :: userName :: realName :: group :: car :: time1 :: time2 :: time3 :: _ :: _ :: _ :: superRally :: finished :: comment :: Nil =>
      Entry(
        stageNumber.toInt,
        stageName,
        userName,
        // until https://discord.com/channels/723091638951608320/792825986055798825/1114861057035489341 is fixed
        if group.isEmpty then "Rally 3" else group,
        car,
        Try(BigDecimal(time3)).toOption.getOrElse(0),
        superRally == "1",
        finished == "F",
        comment
      )
    case _ => ???
  }

def parsePressRally(csv: String) =
  def parseTimestamp(ts: String) = ts.replace(" (N)", "") match {
    case s"$h:$m:$s.$ms" => BigDecimal(h.toInt * 3600 + m.toInt * 60 + s.toInt) + BigDecimal(s"0.$ms")
    case _               => BigDecimal(0)
  }

  val (header :: data) = csv.split('\n').toList: @unchecked
  val stages = header.split(";", -1).drop(6).init.zipWithIndex
  data.map(_.split(";", -1).toList).flatMap {
    case _ :: _ :: realName :: _ :: group :: car :: times =>
      times.init.zip(stages).map { case (time, (stageName, stageNumber)) =>
        Entry(
          stageNumber + 1,
          stageName,
          realName,
          group,
          car,
          parseTimestamp(time),
          false,
          time != "",
          "",
          time.contains("(N)")
        )
      }
    case _ => ???
  }

def stages(entries: List[Entry]) =
  entries.map(e => Stage(e.stageNumber, e.stageName)).distinct.sortBy(_.number)

def results(entries: List[Entry]) =
  val withOverall = entries
    .groupBy(_.userName)
    .view
    .mapValues { results =>
      val overallTimes = results.scanLeft(BigDecimal(0))((sofar, entry) => sofar + entry.stageTime)
      results
        .zip(overallTimes.drop(1))
        .map((e, overall) =>
          TimeResult(e.stageNumber, e.stageName, e.userName, e.stageTime, overall, e.superRally, e.finished, e.comment)
        )
    }
    .values
    .flatten

  val retired = withOverall.filterNot(_.finished).map(_.userName).toSet

  withOverall.groupBy(r => Stage(r.stageNumber, r.stageName)).view.mapValues { results =>
    val stageResults = results.toList.filter(_.finished).sortBy(_.stageTime)
    val overallResults = results.toList.filter(_.finished).sortBy(_.overallTime)
    overallResults.zipWithIndex.map { (result, overall) =>
      PositionResult(
        result.stageNumber,
        result.userName,
        stageResults.indexOf(result) + 1,
        overall + 1,
        result.stageTime,
        result.overallTime,
        result.superRally,
        !retired.contains(result.userName),
        result.comment
      )
    }
  }

def drivers(results: MapView[Stage, List[PositionResult]]) =
  results
    .flatMap((stage, positionResults) =>
      positionResults.map(r =>
        DriverResults(
          r.userName,
          List(
            PositionResult(
              stage.number,
              r.userName,
              r.stagePosition,
              r.overallPosition,
              r.stageTime,
              r.overallTime,
              r.superRally,
              r.rallyFinished,
              r.comment
            )
          )
        )
      )
    )
    .groupBy(_.name)
    .map((name, results) => DriverResults(results.head.name, results.flatMap(_.results).toList.sortBy(_.stageNumber)))
    .toList
    .sortBy(_.name)

def rally(id: Int, name: String, entries: List[Entry]) =
  val groupResults = entries.groupBy(_.group).map { case (group, entries) =>
    GroupResults(group, results(entries) pipe drivers)
  }
  val carResults = entries.groupBy(e => (e.group, e.car)).map { case ((group, car), entries) =>
    CarResults(car, group, results(entries) pipe drivers)
  }

  RallyData(
    id,
    name,
    Instant.now(),
    stages(entries),
    results(entries) pipe drivers,
    groupResults.toList,
    carResults.toList
  )
