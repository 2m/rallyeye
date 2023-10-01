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

import typings.countryEmoji
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
import com.raquo.laminar.codecs.StringAsIsCodec
import org.scalajs.dom
import org.scalajs.dom.HTMLElement
import rallyeye.shared._

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

def getColorScale(drivers: js.Array[DriverResults]) =
  scaleOrdinal(schemeCategory10).domain(drivers.map(d => d.driver.userName))

val positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
  .unknown("#000000")
  .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

val textLength = svgAttr[String]("textLength", StringAsIsCodec, None)
val lengthAdjust = svgAttr[String]("lengthAdjust", StringAsIsCodec, None)

@main
def main() =
  L.renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App {

  val rallyData = Var(RallyData.empty)
  val rallyDataSignal = rallyData.signal
  val resultFilter = Var(ResultFilter.AllResultsId)

  val stagesSignal = rallyDataSignal.map(_.stages)
  val driversSignal =
    rallyDataSignal.combineWith(resultFilter.signal).map((data, filter) => ResultFilter.entries(data)(filter))

  val xScale = stagesSignal.map(s => getXScale(s.toJSArray))
  val yScale = driversSignal.map(d => getYScale(d.toJSArray))
  val scale = xScale.combineWith(yScale)
  val colorScale = driversSignal.map(drivers => getColorScale(drivers.toJSArray))

  val selectedDriver = Var(Option.empty[Driver])
  val driverSelectionBus = EventBus[Driver]()
  val selectDriver = Observer[Driver](
    onNext = driver =>
      Var.set(
        selectedDriver -> Some(driver),
        selectedResult -> None
      )
  )

  val selectedResult = Var(Option.empty[DriverResult])
  val resultSelectionBus = EventBus[DriverResult]()
  val selectResult = selectedResult.someWriter

  var fillDriverNames = Var(Option.empty[Unit])

  import Router._
  val app = L.div(
    L.child <-- router.currentPageSignal.map(renderPage)
  )

  def fetchData(rallyId: Int, endpoint: Endpoint) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global

    fetch(rallyId, endpoint).map { rallyData =>
      Var.set(
        App.rallyData -> rallyData,
        App.selectedDriver -> None,
        App.selectedResult -> None
      )
    }

  def renderPage(page: Page) =
    page match {
      case IndexPage => indexPage()
      case RallyPage(rallyId, results) =>
        if rallyData.now().id != rallyId then fetchData(rallyId, dataEndpoint)
        Var.set(resultFilter -> results, fillDriverNames -> None)
        rallyPage()
      case PressAuto(year, results) =>
        if rallyData.now().id != year then fetchData(year, pressAutoEndpoint)
        Var.set(resultFilter -> results, fillDriverNames -> Some(()))
        rallyPage()
    }

  def indexPage() =
    router.replaceState(RallyPage(48272, ResultFilter.AllResultsId)) // rally to show by default
    L.div()

  def rallyPage() =
    L.div(
      L.cls := "grid",
      Components.header(rallyDataSignal, resultFilter.signal),
      L.child <-- selectedResult.signal.map(r => if r.isDefined then renderInfo() else emptyNode),
      L.div(
        L.cls := "row-start-2 col-start-1 p-4 text-xs overflow-scroll",
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

  def renderStagePosition(result: DriverResult) =
    (result.superRally, result.nominal) match {
      case (true, _) => "SR"
      case (_, true) => "N"
      case _         => result.stagePosition.toString
    }

  def renderInfo() =
    L.div(
      L.cls := "w-44 h-44 sticky row-start-2 col-start-1 top-4 left-4 mt-4 p-4 text-xs border-2 bg-white",
      children <-- (
        Signal
          .combine(selectedResult, selectedDriver, stagesSignal)
          .mapN { (maybeResult, maybeDriver, stages) =>
            (maybeResult, maybeDriver) match {
              case (Some(result), Some(driver)) =>
                Seq(
                  L.div(s"SS${result.stageNumber} ${stages(result.stageNumber - 1).name}"),
                  L.div(renderCountryAndName(driver)),
                  L.div(s"Stage: ${result.stageTime.prettyDiff} (${renderStagePosition(result)})"),
                  L.div(s"Rally: ${result.overallTime.prettyDiff} (${result.overallPosition})"),
                  if result.comment.nonEmpty then L.div(s"“${result.comment}”") else emptyNode
                )
              case _ => Seq(emptyNode)
            }
          }
      )
    )

  def renderCountryAndName(driver: Driver) =
    Seq(
      countryEmoji.mod.flag(driver.country).toOption.fold(emptyNode) { flag =>
        L.span(
          countryEmoji.mod.name(driver.country).toOption.fold(emptyNode)(L.aria.label := _),
          flag + " "
        )
      },
      L.span(renderFullName(driver))
    )

  def renderCountry(country: String) =
    countryEmoji.mod.flag(country).toOption.getOrElse("🏴") + " "

  def renderFullName(driver: Driver) =
    val parts = driver.userName :: (if driver.realName.nonEmpty then driver.realName :: Nil else Nil)
    parts.mkString(" / ")

  def renderDriver(driverResults: DriverResults) =
    g(
      cls := "clickable",
      transform <-- yScale.map(y => s"translate(0, ${y(driverResults.results(0).overallPosition)})"),
      text(
        renderCountry(driverResults.driver.country),
        renderFullName(driverResults.driver),
        dy := "0.4em",
        textLength <-- fillDriverNames.signal.map(_.fold("none")(_ => "185")),
        lengthAdjust := "spacingAndGlyphs"
      ),
      L.onClick.map(_ => driverResults.driver) --> driverSelectionBus.writer,
      opacity <-- selectedDriver.signal.map(d =>
        d.map(d => if d == driverResults.driver then "1" else "0.2").getOrElse("1")
      )
    )

  def renderStage(stage: Stage, idx: Int) =
    g(
      transform <-- xScale.map(x => s"translate(${x(idx * 2)}, 0)"),
      text(stage.name, x := "20", dy := "0.35em", transform := s"translate(0, ${RallyEye.margin.top}) rotate(-90)")
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
        renderStagePosition(result),
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
      opacity <-- selectedDriver.signal.map(d =>
        d.map(d => if d == driverResults.driver then "1" else "0.2").getOrElse("1")
      ),
      rallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(false)),
      crashResults.map(mkCrashAndRecoveryLine),
      superRallyResults.map(_.map(resultIdxToCoords)).map(mkResultLine(true)),
      retired.map { case (result, idx) => mkCrashLine(result, None, idx) },
      driverResults.results.zipWithIndex.map(mkResultCircle),
      driverResults.results.zipWithIndex.map(mkResultNumber)
    )
}
