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

package components

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import typings.d3Scale.mod.ScaleOrdinal_
import typings.d3Scale.mod.scaleLinear
import typings.d3Scale.mod.scaleOrdinal
import typings.d3ScaleChromatic.mod.schemeCategory10
import typings.d3Shape.mod.line

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api._
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.children
import com.raquo.laminar.api.L.emptyNode
import com.raquo.laminar.api.L.seqToModifier
import com.raquo.laminar.api.L.svg._
import rallyeye.shared._

object ResultLines:
  val colWidth = 28
  val rowHeight = 28

  case class Margin(top: Int, right: Int, bottom: Int, left: Int)
  val margin = Margin(rowHeight / 2, 0, rowHeight / 2, colWidth)

  def renderStagePosition(result: DriverResult) =
    (result.superRally, result.nominal) match {
      case (true, _) => "SR"
      case (_, true) => "N"
      case _         => result.stagePosition.toString
    }

case class ResultLines(
    stagesSignal: Signal[Option[List[Stage]]],
    driversSignal: Signal[Option[List[DriverResults]]],
    selectedDriverSignal: Signal[Option[Driver]],
    driverSelectionBus: EventBus[Driver],
    selectDriver: Observer[Driver],
    selectResult: Observer[DriverResult]
):
  val xScale = stagesSignal.map(s => getXScale(s.toList.flatten.toJSArray))
  val yScale = driversSignal.map(d => getYScale(d.toList.flatten.toJSArray))
  val scale = xScale.combineWith(yScale)
  val colorScale = driversSignal.map(drivers => getColorScale(drivers.toList.flatten.toJSArray))
  val positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
    .unknown("#000000")
    .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

  val resultSelectionBus = EventBus[DriverResult]()

  def getXScale(stages: js.Array[Stage]) =
    scaleLinear()
      .domain(js.Array(0, columns(stages.size)))
      .range(
        js.Array(
          ResultLines.margin.left,
          ResultLines.margin.left + columns(stages.size) * ResultLines.colWidth
        )
      )

  def getYScale(drivers: js.Array[DriverResults]) = scaleLinear()
    .domain(js.Array(1, drivers.flatMap(_.results.map(_.overallPosition)).max))
    .range(js.Array(ResultLines.margin.top, ResultLines.margin.top + drivers.size * ResultLines.rowHeight))

  def getColorScale(drivers: js.Array[DriverResults]) =
    scaleOrdinal(schemeCategory10).domain(drivers.map(d => d.driver.userName))

  def columns(n: Int) = n * 2 - 1 // double the x domain to have a spot for crash icon
  def canvasWidth(stages: List[Stage]) =
    ResultLines.margin.left + columns(stages.size) * ResultLines.colWidth + ResultLines.margin.right
  def canvasHeight(drivers: List[DriverResults]) =
    ResultLines.margin.top + drivers.size * ResultLines.rowHeight + ResultLines.margin.bottom

  def render() =
    svg(
      width <-- stagesSignal.map(s => canvasWidth(s.toList.flatten)).map(_.toString),
      height <-- driversSignal.map(d => canvasHeight(d.toList.flatten)).map(_.toString),
      children <-- driversSignal.map(drivers => drivers.toList.flatten.map(renderResultLine)),
      resultSelectionBus.events --> selectResult
    )

  def renderResultLine(driverResults: DriverResults) =
    def mkResultLine(superRally: Boolean)(coordinates: List[(Int, Int)]) =
      path(
        fill := "none",
        stroke <-- colorScale.map(color => color(driverResults.driver.userName)),
        if superRally then strokeDashArray := "1 0 1" else emptyNode,
        d <-- scale.mapN { (x, y) =>
          line[(Int, Int)]()
            .x((r, _, _) => x(r._1))
            .y((r, _, _) => y(r._2))(coordinates.toJSArray)
        }
      )

    def resultIdxToCoords(result: DriverResult, idx: Int) = (idx * 2, result.overallPosition)

    def mkCrashLine(
        lastStageResult: DriverResult,
        superRallyStageResult: Option[DriverResult],
        lastStageResultIdx: Int
    ) =
      Seq(
        mkResultLine(false)(
          List(
            (lastStageResultIdx * 2, lastStageResult.overallPosition),
            (lastStageResultIdx * 2 + 1, lastStageResult.overallPosition)
          )
        ),
        superRallyStageResult
          .map { result =>
            mkResultLine(true)(
              List(
                (lastStageResultIdx * 2 + 1, lastStageResult.overallPosition),
                (lastStageResultIdx * 2 + 2, result.overallPosition)
              )
            )
          }
          .getOrElse(emptyNode),
        mkCrashCircle(lastStageResultIdx * 2 + 1, lastStageResult.overallPosition),
        text(
          fontSize := "16px",
          transform <-- scale.mapN { (x, y) =>
            s"translate(${x(lastStageResultIdx * 2 + 1)},${y(lastStageResult.overallPosition)})"
          },
          dy := "0.35em",
          textAnchor := "middle",
          "💥"
        )
      )

    def mkCrashAndRecoveryLine(
        lastStageResult: DriverResult,
        superRallyStageResult: DriverResult,
        lastStageResultIdx: Int
    ) =
      mkCrashLine(lastStageResult, Some(superRallyStageResult), lastStageResultIdx)

    def mkResultCircle(result: DriverResult, idx: Int) =
      g(
        cls := "clickable",
        transform <-- scale.mapN { (x, y) =>
          s"translate(${x(idx * 2)},${y(result.overallPosition)})"
        },
        circle(
          stroke := "white",
          fill := positionColorScale(result.stagePosition),
          r := "12",
          // #TODO: Combine these two into a single  `L.onClick --> { _ => EventBus.emit(...) }` once Airstream fixes that method's type signature
          L.onClick.map(_ => driverResults.driver) --> driverSelectionBus.writer,
          L.onClick.map(_ => result) --> resultSelectionBus.writer
        ),
        if result.comment.nonEmpty then
          circle(
            fill := "black",
            r := "2",
            transform := "translate(10, -10)"
          )
        else emptyNode
      )

    def mkCrashCircle(x: Int, y: Int) =
      circle(
        fill := "white",
        transform <-- scale.mapN { (xScale, yScale) =>
          s"translate(${xScale(x)},${yScale(y)})"
        },
        r := "6"
      )

    def mkResultNumber(result: DriverResult, idx: Int) =
      text(
        cls := "clickable",
        ResultLines.renderStagePosition(result),
        transform <-- scale.mapN { (x, y) =>
          s"translate(${x(idx * 2)},${y(result.overallPosition)})"
        },
        dy := "0.35em",
        fill := "white",
        stroke := "white",
        strokeWidth := "1",
        textAnchor := "middle",
        L.onClick.map(_ => driverResults.driver) --> driverSelectionBus.writer,
        L.onClick.map(_ => result) --> resultSelectionBus.writer
      )

    val (rallyResultsWoLast, superRallyResultsWoLast, lastStint, lastSuperRally) =
      driverResults.results.zipWithIndex.foldLeft(
        (
          List.empty[List[(DriverResult, Int)]],
          List.empty[List[(DriverResult, Int)]],
          List.empty[(DriverResult, Int)],
          false
        )
      ) { case ((rallyResults, superRallyResults, acc, lastSuperRally), resWithIdx @ (result, idx)) =>
        if lastSuperRally == result.superRally then
          (rallyResults, superRallyResults, acc :+ resWithIdx, result.superRally)
        else {
          if lastSuperRally then
            (rallyResults, superRallyResults :+ acc, acc.lastOption.toList :+ resWithIdx, result.superRally)
          else (rallyResults :+ acc, superRallyResults, List(resWithIdx), result.superRally)
        }
      }

    val rallyResults = if lastSuperRally then rallyResultsWoLast else rallyResultsWoLast :+ lastStint
    val superRallyResults = if lastSuperRally then superRallyResultsWoLast :+ lastStint else superRallyResultsWoLast

    val crashResults = superRallyResults.zipWithIndex.flatMap { case (results, idx) =>
      val (superRallyResult, _) = results.head
      rallyResults(idx).lastOption.map { case (lastStageResult, lastStageIdx) =>
        (lastStageResult, superRallyResult, lastStageIdx)
      }
    }

    val retired = {
      val (lastResult, lastIdx) = lastStint.last
      if lastResult.rallyFinished then None else Some(lastResult, lastIdx)
    }

    g(
      strokeWidth := "2",
      opacity <-- selectedDriverSignal.map(d =>
        d.map(d => if d == driverResults.driver then "1" else "0.2").getOrElse("1")
      ),
      rallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(false)),
      crashResults.map(mkCrashAndRecoveryLine),
      superRallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(true)),
      retired.map { case (result, idx) => mkCrashLine(result, None, idx) },
      driverResults.results.zipWithIndex.map(mkResultCircle),
      driverResults.results.zipWithIndex.map(mkResultNumber)
    )
