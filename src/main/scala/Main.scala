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
case class Result(stageNumber: Int, position: Int, overall: Int, superRally: Boolean)
case class Driver(name: String, results: List[Result])

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  val width = 1000
  val height = 1100
  val margin = Margin(130, 30, 50, 130)

def xScale(stages: js.Array[Stage]) = scaleLinear()
  .domain(js.Array(0, stages.size - 1))
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
    App.results.update(_ => r)
  }

  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.appElement())

object App {
  val results = Var(Map.empty[Stage, List[PositionResult]].view)
  val stagesSignal = results.signal.map(getStages)
  val driversSignal = results.signal.map(getDrivers)

  var selectedDriver = Var(Option.empty[String])

  val driverSelectionBus = EventBus[Driver]()
  val selectDriver = Observer[Driver](onNext = d => selectedDriver.set(Some(d.name)))

  val startOffset = customSvgAttr("startOffset", StringAsIsCodec)

  def appElement() =
    svg(
      width := RallyEye.width.toString,
      height := RallyEye.height.toString,
      fontSize := "12px",
      fontFamily := "Tahoma",
      children <-- driversSignal.map(drivers => drivers.map(renderDriver)),
      children <-- stagesSignal.map(stages => stages.zipWithIndex.toSeq.map(renderStage)),
      children <-- driversSignal.map(drivers => drivers.map(renderResultLine))
    )

  def renderDriver(driver: Driver) =
    g(
      // font := "12px sans-serif",
      transform <-- driversSignal.map(drivers =>
        s"translate(0, ${yScale(drivers.toJSArray)(driver.results(0).overall)})"
      ),
      text(driver.name, dy := "0.4em"),
      onMouseOver.map(_ => driver) --> driverSelectionBus.writer,
      driverSelectionBus.events --> selectDriver,
      opacity <-- selectedDriver.signal.map(d => d.map(d => if d == driver.name then "1" else "0.2").getOrElse("1"))
    )

  def renderStage(stage: Stage, idx: Int) =
    g(
      transform <-- stagesSignal.map(stages => s"translate(${xScale(stages.toJSArray)(idx)}, 0)"),
      // top stage name
      text(stage.name, x := "20", dy := "0.35em", transform := s"translate(0, ${RallyEye.margin.top}) rotate(-90)"),
      // bottom stage name
      text(
        stage.name,
        textAnchor := "end",
        x := "-20",
        dy := "0.35em",
        transform := s"translate(0, ${RallyEye.height - RallyEye.margin.top}) rotate(-90)"
      )
    )

  def renderResultLine(driver: Driver) =
    def mkLine(stages: js.Array[Stage], drivers: js.Array[Driver]) =
      line[(Result, Int)]()
        .x((r, _, _) => xScale(stages)(r._2))
        .y((r, _, _) => yScale(drivers)(r._1.overall))

    def mkResultLine(results: List[(Result, Int)], superRally: Boolean) =
      path(
        fill := "none",
        stroke <-- driversSignal.map(drivers => colorScale(drivers.toJSArray)(driver.name)),
        if superRally then strokeDashArray := "1 0 1" else emptyNode,
        d <-- (
          for
            stages <- stagesSignal
            drivers <- driversSignal
          yield mkLine(stages.toJSArray, drivers.toJSArray)(results.toJSArray)
        )
      )

    def mkCrashLine(results: List[(Result, Int)]) =
      Seq(
        path(
          idAttr := s"${driver.hashCode}-crash-line",
          fill := "none",
          stroke <-- driversSignal.map(drivers => colorScale(drivers.toJSArray)(driver.name)),
          strokeDashArray := "1 0 1",
          dy := "5em",
          d <-- (
            for
              stages <- stagesSignal
              drivers <- driversSignal
            yield mkLine(stages.toJSArray, drivers.toJSArray)(results.toJSArray)
          )
        ),
        text(
          textAnchor := "middle",
          fontSize := "20px",
          textPath(
            href := s"#${driver.hashCode}-crash-line",
            startOffset := "1em",
            "💥"
          )
        )
      )

    def mkResultCircle(result: Result, idx: Int) =
      circle(
        stroke := "white",
        fill := positionColorScale(result.position),
        transform <-- stagesSignal.flatMap(stages =>
          driversSignal.map(drivers =>
            s"translate(${xScale(stages.toJSArray)(idx)},${yScale(drivers.toJSArray)(result.overall)})"
          )
        ),
        r := "12",
        onMouseOver.map(_ => driver) --> driverSelectionBus.writer
      )

    def mkResultNumber(result: Result, idx: Int) =
      text(
        result.position,
        transform <-- stagesSignal.flatMap(stages =>
          driversSignal.map(drivers =>
            s"translate(${xScale(stages.toJSArray)(idx)},${yScale(drivers.toJSArray)(result.overall)})"
          )
        ),
        dy := "0.35em",
        fill := "white",
        stroke := "white",
        strokeWidth := "1",
        textAnchor := "middle",
        onMouseOver.map(_ => driver) --> driverSelectionBus.writer
      )

    val (rallyResults, superRallyResults) = driver.results.zipWithIndex.span((r, _) => !r.superRally)

    g(
      strokeWidth := "2",
      opacity <-- selectedDriver.signal.map(d => d.map(d => if d == driver.name then "1" else "0.2").getOrElse("1")),
      mkResultLine(rallyResults, false),
      mkCrashLine(List(rallyResults.lastOption, superRallyResults.headOption).flatten),
      mkResultLine(superRallyResults, true),
      Seq(driver.results.zipWithIndex.map(mkResultCircle)),
      Seq(driver.results.zipWithIndex.map(mkResultNumber))
    )
}
