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

import scala.collection.MapView
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try
import scala.util.chaining._

import sttp.client3._

case class Entry(
    stageNumber: Int,
    stageName: String,
    userName: String,
    stageTime: Option[BigDecimal],
    overallTime: Option[BigDecimal],
    superRally: Boolean,
    finished: Boolean
)
case class TimeResult(userName: String, stageTime: BigDecimal, overallTime: BigDecimal, finished: Boolean)
case class PositionResult(userName: String, stagePosition: Int, overallPosition: Int)

def parse(csv: String) =
  val (header :: data) = csv.split('\n').toList: @unchecked
  val parsed = data.map(_.split(";", -1).toList).map {
    case stageNumber :: stageName :: _ :: userName :: _ :: _ :: _ :: _ :: _ :: time3 :: _ :: _ :: _ :: superRally :: finished :: _ :: Nil =>
      Entry(
        stageNumber.toInt,
        stageName,
        userName,
        Try(BigDecimal(time3)).toOption,
        None,
        superRally == "1",
        finished == "F"
      )
    case _ => ???
  }
  fromEntries(parsed)

def fromEntries(entries: List[Entry]) =
  val withOverall = entries
    .groupBy(_.userName)
    .view
    .mapValues { results =>
      val overallTimes =
        results.scanLeft(BigDecimal(0))((sofar, entry) => sofar + entry.stageTime.getOrElse(BigDecimal(0)))
      results.zip(overallTimes.drop(1)).map((e, overall) => e.copy(overallTime = Some(overall)))
    }
    .values
    .flatten

  val grouped = withOverall
    .groupBy(entry => Stage(entry.stageNumber, entry.stageName))
    .view
    .mapValues(v =>
      v.collect { case Entry(_, _, userName, Some(stageTime), Some(overallTime), _, finished) =>
        TimeResult(userName, stageTime, overallTime, finished)
      }
    )

  val positions = grouped.mapValues { results =>
    val stageResults = results.toList.filter(_.finished).sortBy(_.stageTime)
    val overallResults = results.toList.filter(_.finished).sortBy(_.overallTime)
    overallResults.zipWithIndex.map { (result, overall) =>
      PositionResult(result.userName, stageResults.indexOf(result) + 1, overall + 1)
    }
  }

  positions

def getStages(results: MapView[Stage, List[PositionResult]]) =
  results.keys

def getDrivers(results: MapView[Stage, List[PositionResult]]) =
  results
    .flatMap((stage, positionResults) =>
      positionResults.map(r => Driver(r.userName, List(Result(stage.number, r.stagePosition, r.overallPosition))))
    )
    .groupBy(_.name)
    .map((name, results) => Driver(results.head.name, results.flatMap(_.results).toList.sortBy(_.stageNumber)))
    .toList
    .sortBy(_.name)

def fetch() =
  val backend = FetchBackend()
  val response = basicRequest
    .get(uri"results.csv")
    .send(backend)

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  response.map(_.body.getOrElse("") pipe parse)
