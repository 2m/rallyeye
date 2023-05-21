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

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import typings.d3Scale.mod.ScaleOrdinal_
import typings.d3Scale.mod.scaleLinear
import typings.d3Scale.mod.scaleOrdinal
import typings.d3ScaleChromatic.mod.schemeCategory10
import typings.d3Selection.mod.Selection_
import typings.d3Selection.mod.select
import typings.d3Shape.mod.line

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.eventbus.EventBus.apply
import com.raquo.airstream.state.Var
import com.raquo.airstream.state.Var.apply
import com.raquo.laminar.api._
import com.raquo.laminar.api.L
import com.raquo.laminar.api.L.children
import com.raquo.laminar.api.L.emptyNode
import com.raquo.laminar.api.L.seqToModifier
import com.raquo.laminar.api.L.svg._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import rallyeye.shared._

case class Rally(id: Int, name: String)

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  def columns(n: Int) = n * 2 - 1 // double the x domain to have a spot for crash icon
  def width(stages: List[Stage]) = margin.left + columns(stages.size) * colWidth + margin.right
  def height(drivers: List[DriverResults]) = margin.top + drivers.size * rowHeight + margin.bottom
  val margin = Margin(200, 0, 28, 200)

  val colWidth = 28
  val rowHeight = 28

def getXScale(stages: js.Array[Stage]) =
  scaleLinear()
    .domain(js.Array(0, RallyEye.columns(stages.size)))
    .range(
      js.Array(
        RallyEye.margin.left,
        RallyEye.margin.left + RallyEye.columns(stages.size) * RallyEye.colWidth
      )
    )

def getYScale(drivers: js.Array[DriverResults]) = scaleLinear()
  .domain(js.Array(1, drivers.flatMap(_.results.map(_.overallPosition)).max))
  .range(js.Array(RallyEye.margin.top, RallyEye.margin.top + drivers.size * RallyEye.rowHeight))

def getColorScale(drivers: js.Array[DriverResults]) = scaleOrdinal(schemeCategory10).domain(drivers.map(d => d.name))

val positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
  .unknown("#000000")
  .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

@main
def main() =
  L.renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App {

  val rallyData = Var(RallyData.empty)
  val rallyDataSignal = rallyData.signal
  val stagesSignal = rallyDataSignal.map(_.stages)
  val driversSignal = rallyDataSignal.map(_.allResults)

  val selectedRally = Var(Option.empty[Rally])

  val xScale = stagesSignal.map(s => getXScale(s.toJSArray))
  val yScale = driversSignal.map(d => getYScale(d.toJSArray))
  val scale = xScale.combineWith(yScale)
  val colorScale = rallyDataSignal.map(data => getColorScale(data.allResults.toJSArray))

  val selectedDriver = Var(Option.empty[String])
  val driverSelectionBus = EventBus[DriverResults]()
  val selectDriver = Observer[DriverResults](
    onNext = d =>
      Var.set(
        selectedDriver -> Some(d.name),
        selectedResult -> None
      )
  )

  val selectedResult = Var(Option.empty[PositionResult])
  val resultSelectionBus = EventBus[PositionResult]()
  val selectResult = selectedResult.someWriter

  import Router._
  val app = L.div(
    L.child <-- router.currentPageSignal.map(renderPage)
  )

  def fetchData(rallyId: Int) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

    fetch(rallyId).map { rallyData =>
      Var.set(
        App.selectedRally -> Some(Rally(rallyId, rallyData.name)),
        App.rallyData -> rallyData,
        App.selectedDriver -> None,
        App.selectedResult -> None
      )
    }

  def renderPage(page: Page) =
    page match {
      case IndexPage => indexPage()
      case RallyPage(rallyId) =>
        fetchData(rallyId)
        rallyPage()
    }

  def indexPage() =
    router.replaceState(RallyPage(48272)) // rally to show by default
    L.div()

  def rallyPage() =
    L.div(
      L.child <-- selectedResult.signal.map(r => if r.isDefined then renderInfo() else emptyNode),
      Components.header(selectedRally.signal),
      L.div(
        L.cls := "graph p-4 text-xs overflow-scroll",
        svg(
          width <-- stagesSignal.map(s => RallyEye.width(s)).map(_.toString),
          height <-- driversSignal.map(d => RallyEye.height(d)).map(_.toString),
          children <-- driversSignal.map(drivers => drivers.map(renderDriver)),
          children <-- stagesSignal.map(stages => stages.zipWithIndex.toSeq.map(renderStage)),
          children <-- driversSignal.map(drivers => drivers.map(renderResultLine)),
          driverSelectionBus.events --> selectDriver,
          resultSelectionBus.events --> selectResult
        )
      )
    )

  def renderInfo() =
    L.div(
      L.cls := "info fixed p-4 text-xs border-2 bg-white",
      children <-- (
        Signal
          .combine(selectedResult, selectedDriver, stagesSignal)
          .mapN { (maybeResult, driver, stages) =>
            maybeResult match {
              case None => Seq(emptyNode)
              case Some(result) =>
                Seq(
                  L.div(s"SS${result.stageNumber} ${stages(result.stageNumber - 1).name}"),
                  L.div(driver),
                  L.div(s"Stage: ${result.stageTime.prettyDiff} (${result.stagePosition})"),
                  L.div(s"Rally: ${result.overallTime.prettyDiff} (${result.overallPosition})"),
                  if result.comment.nonEmpty then L.div(s"“${result.comment}”") else emptyNode
                )
            }
          }
      )
    )

  def renderDriver(driver: DriverResults) =
    g(
      cls := "clickable",
      transform <-- yScale.map(y => s"translate(0, ${y(driver.results(0).overallPosition)})"),
      text(driver.name, dy := "0.4em"),
      L.onClick.map(_ => driver) --> driverSelectionBus.writer,
      opacity <-- selectedDriver.signal.map(d => d.map(d => if d == driver.name then "1" else "0.2").getOrElse("1"))
    )

  def renderStage(stage: Stage, idx: Int) =
    g(
      transform <-- xScale.map(x => s"translate(${x(idx * 2)}, 0)"),
      text(stage.name, x := "20", dy := "0.35em", transform := s"translate(0, ${RallyEye.margin.top}) rotate(-90)")
    )

  def renderResultLine(driver: DriverResults) =
    def mkResultLine(superRally: Boolean)(coordinates: List[(Int, Int)]) =
      path(
        fill := "none",
        stroke <-- colorScale.map(color => color(driver.name)),
        if superRally then strokeDashArray := "1 0 1" else emptyNode,
        d <-- scale.mapN { (x, y) =>
          line[(Int, Int)]()
            .x((r, _, _) => x(r._1))
            .y((r, _, _) => y(r._2))(coordinates.toJSArray)
        }
      )

    def resultIdxToCoords(result: PositionResult, idx: Int) = (idx * 2, result.overallPosition)

    def mkCrashLine(
        lastStageResult: PositionResult,
        superRallyStageResult: Option[PositionResult],
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
        lastStageResult: PositionResult,
        superRallyStageResult: PositionResult,
        lastStageResultIdx: Int
    ) =
      mkCrashLine(lastStageResult, Some(superRallyStageResult), lastStageResultIdx)

    def mkResultCircle(result: PositionResult, idx: Int) =
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
          L.onClick.map(_ => driver) --> driverSelectionBus.writer,
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

    def mkResultNumber(result: PositionResult, idx: Int) =
      text(
        cls := "clickable",
        result.stagePosition,
        transform <-- scale.mapN { (x, y) =>
          s"translate(${x(idx * 2)},${y(result.overallPosition)})"
        },
        dy := "0.35em",
        fill := "white",
        stroke := "white",
        strokeWidth := "1",
        textAnchor := "middle",
        L.onClick.map(_ => driver) --> driverSelectionBus.writer,
        L.onClick.map(_ => result) --> resultSelectionBus.writer
      )

    val (rallyResultsWoLast, superRallyResultsWoLast, lastStint, lastSuperRally) = driver.results.zipWithIndex.foldLeft(
      (
        List.empty[List[(PositionResult, Int)]],
        List.empty[List[(PositionResult, Int)]],
        List.empty[(PositionResult, Int)],
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
      opacity <-- selectedDriver.signal.map(d => d.map(d => if d == driver.name then "1" else "0.2").getOrElse("1")),
      rallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(false)),
      crashResults.map(mkCrashAndRecoveryLine),
      superRallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(true)),
      retired.map { case (result, idx) => mkCrashLine(result, None, idx) },
      driver.results.zipWithIndex.map(mkResultCircle),
      driver.results.zipWithIndex.map(mkResultNumber)
    )
}
