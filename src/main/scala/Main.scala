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

import com.raquo.airstream.core.Observer
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.eventbus.EventBus.apply
import com.raquo.airstream.state.Var
import com.raquo.airstream.state.Var.apply
import com.raquo.domtypes.generic.codecs.StringAsIsCodec
import com.raquo.laminar.api._
import com.raquo.laminar.api.L.children
import com.raquo.laminar.api.L.emptyNode
import com.raquo.laminar.api.L.onMouseOver
import com.raquo.laminar.api.L.renderOnDomContentLoaded
import com.raquo.laminar.api.L.seqToModifier
import com.raquo.laminar.api.L.svg._
import org.scalajs.dom
import org.scalajs.dom.HTMLElement

case class Stage(number: Int, name: String)
case class Result(
    stageNumber: Int,
    position: Int,
    overall: Int,
    time: BigDecimal,
    overallTime: BigDecimal,
    superRally: Boolean,
    rallyFinished: Boolean,
    comment: String
)
case class Driver(name: String, results: List[Result])

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  val width = 1000
  val height = 1500
  val margin = Margin(200, 30, 50, 200)

def xScale(stages: js.Array[Stage]) = scaleLinear()
  // double the x domain to have a spot for crash icon
  // you can not crash after the last stage, therefore one less
  .domain(js.Array(0, (stages.size - 1) * 2 - 1))
  .range(js.Array(RallyEye.margin.left, RallyEye.width - RallyEye.margin.right))

def yScale(drivers: js.Array[Driver]) = scaleLinear()
  .domain(js.Array(1, drivers.flatMap(_.results.map(_.overall)).max))
  .range(js.Array(RallyEye.margin.top, RallyEye.height - RallyEye.margin.bottom))

def colorScale(drivers: js.Array[Driver]) = scaleOrdinal(schemeCategory10).domain(drivers.map(d => d.name).toJSArray)

def positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
  .unknown("#000000")
  .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

@main
def main() =
  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  fetch().map { r =>
    App.results.update(_ => r // .mapValues(_.filter(_.userName == ""))
    )
  }

  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.appElement())

object App {
  val results = Var(Map.empty[Stage, List[PositionResult]].view)
  val stagesSignal = results.signal.map(getStages)
  val driversSignal = results.signal.map(getDrivers)

  val selectedDriver = Var(Option.empty[String])
  val driverSelectionBus = EventBus[Driver]()
  val selectDriver = Observer[Driver](onNext = d => selectedDriver.set(Some(d.name)))

  val selectedResult = Var(Option.empty[Result])
  val resultSelectionBus = EventBus[Result]()
  val selectResult = Observer[Result](onNext = r => selectedResult.set(Some(r)))

  def appElement() =
    svg(
      width := RallyEye.width.toString,
      height := RallyEye.height.toString,
      fontSize := "12px",
      fontFamily := "Tahoma",
      renderInfo(),
      children <-- driversSignal.map(drivers => drivers.map(renderDriver)),
      children <-- stagesSignal.map(stages => stages.zipWithIndex.toSeq.map(renderStage)),
      children <-- driversSignal.map(drivers => drivers.map(renderResultLine)),
      driverSelectionBus.events --> selectDriver,
      resultSelectionBus.events --> selectResult
    )

  def renderInfo() =
    g(
      children <-- (
        for
          maybeResult <- selectedResult.signal
          driver <- selectedDriver.signal
          stages <- stagesSignal
        yield maybeResult match {
          case None => Seq(emptyNode)
          case Some(result) =>
            Seq(
              text(
                tspan(x := "0", dy := "1.2em", s"SS${result.stageNumber} ${stages(result.stageNumber - 1).name}"),
                tspan(x := "0", dy := "1.2em", driver),
                tspan(x := "0", dy := "1.2em", s"Stage: ${result.time.prettyDuration}"),
                tspan(x := "0", dy := "1.2em", s"Rally: ${result.overallTime.prettyDuration}"),
                if result.comment.nonEmpty then tspan(x := "0", dy := "1.2em", s"“${result.comment}”") else emptyNode
              )
            )
        }
      )
    )

  def renderDriver(driver: Driver) =
    g(
      transform <-- driversSignal.map(drivers =>
        s"translate(0, ${yScale(drivers.toJSArray)(driver.results(0).overall)})"
      ),
      text(driver.name, dy := "0.4em"),
      onMouseOver.map(_ => driver) --> driverSelectionBus.writer,
      opacity <-- selectedDriver.signal.map(d => d.map(d => if d == driver.name then "1" else "0.2").getOrElse("1"))
    )

  def renderStage(stage: Stage, idx: Int) =
    g(
      transform <-- stagesSignal.map(stages => s"translate(${xScale(stages.toJSArray)(idx * 2)}, 0)"),
      text(stage.name, x := "20", dy := "0.35em", transform := s"translate(0, ${RallyEye.margin.top}) rotate(-90)")
    )

  def renderResultLine(driver: Driver) =
    def mkLine(stages: js.Array[Stage], drivers: js.Array[Driver]) =
      line[(Int, Int)]()
        .x((r, _, _) => xScale(stages)(r._1))
        .y((r, _, _) => yScale(drivers)(r._2))

    def mkResultLine(superRally: Boolean)(coordinates: List[(Int, Int)]) =
      path(
        fill := "none",
        stroke <-- driversSignal.map(drivers => colorScale(drivers.toJSArray)(driver.name)),
        if superRally then strokeDashArray := "1 0 1" else emptyNode,
        d <-- (
          for
            stages <- stagesSignal
            drivers <- driversSignal
          yield mkLine(stages.toJSArray, drivers.toJSArray)(coordinates.toJSArray)
        )
      )

    def resultIdxToCoords(result: Result, idx: Int) = (idx * 2, result.overall)

    def mkCrashLine(lastStageResult: Result, superRallyStageResult: Option[Result], lastStageResultIdx: Int) =
      Seq(
        mkResultLine(false)(
          List(
            (lastStageResultIdx * 2, lastStageResult.overall),
            (lastStageResultIdx * 2 + 1, lastStageResult.overall)
          )
        ),
        superRallyStageResult
          .map { result =>
            mkResultLine(true)(
              List(
                (lastStageResultIdx * 2 + 1, lastStageResult.overall),
                (lastStageResultIdx * 2 + 2, result.overall)
              )
            )
          }
          .getOrElse(emptyNode),
        mkCrashCircle(lastStageResultIdx * 2 + 1, lastStageResult.overall),
        text(
          fontSize := "16px",
          transform <-- stagesSignal.flatMap(stages =>
            driversSignal.map(drivers =>
              s"translate(${xScale(stages.toJSArray)(lastStageResultIdx * 2 + 1)},${yScale(drivers.toJSArray)(lastStageResult.overall)})"
            )
          ),
          dy := "0.35em",
          textAnchor := "middle",
          "💥"
        )
      )

    def mkCrashAndRecoveryLine(lastStageResult: Result, superRallyStageResult: Result, lastStageResultIdx: Int) =
      mkCrashLine(lastStageResult, Some(superRallyStageResult), lastStageResultIdx)

    def mkResultCircle(result: Result, idx: Int) =
      g(
        transform <-- stagesSignal.flatMap(stages =>
          driversSignal.map(drivers =>
            s"translate(${xScale(stages.toJSArray)(idx * 2)},${yScale(drivers.toJSArray)(result.overall)})"
          )
        ),
        circle(
          stroke := "white",
          fill := positionColorScale(result.position),
          r := "12",
          onMouseOver.map(_ => driver) --> driverSelectionBus.writer,
          onMouseOver.map(_ => result) --> resultSelectionBus.writer
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
        transform <-- (
          for
            stages <- stagesSignal
            drivers <- driversSignal
          yield s"translate(${xScale(stages.toJSArray)(x)},${yScale(drivers.toJSArray)(y)})"
        ),
        r := "6"
      )

    def mkResultNumber(result: Result, idx: Int) =
      text(
        result.position,
        transform <-- stagesSignal.flatMap(stages =>
          driversSignal.map(drivers =>
            s"translate(${xScale(stages.toJSArray)(idx * 2)},${yScale(drivers.toJSArray)(result.overall)})"
          )
        ),
        dy := "0.35em",
        fill := "white",
        stroke := "white",
        strokeWidth := "1",
        textAnchor := "middle",
        onMouseOver.map(_ => driver) --> driverSelectionBus.writer,
        onMouseOver.map(_ => result) --> resultSelectionBus.writer
      )

    println(driver.name)

    val (rallyResultsWoLast, superRallyResultsWoLast, lastStint, lastSuperRally) = driver.results.zipWithIndex.foldLeft(
      (List.empty[List[(Result, Int)]], List.empty[List[(Result, Int)]], List.empty[(Result, Int)], false)
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

    println(rallyResults)
    println(superRallyResults)

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
