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

import scala.util.Random

import typings.countryEmoji
import typings.flowbite.mod.initFlowbite

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.eventbus.EventBus
import com.raquo.laminar.api.L._
import rallyeye._
import rallyeye.shared._

case object RallyResult:
  val writingMode = styleProp[String]("writing-mode")

  val gridTemplateColumns = styleProp[String]("grid-template-columns")
  val contain = styleProp[String]("contain")

case class RallyResult(
    stagesSignal: Signal[List[Stage]],
    driversSignal: Signal[List[DriverResults]],
    selectedDriverSignal: Signal[Option[Driver]],
    selectDriver: Observer[Driver],
    selectResult: Observer[DriverResult],
    selectedResultSignal: Signal[Option[DriverResult]]
):
  import RallyResult._

  val driverSelectionBus = EventBus[Driver]()

  def render() =
    div(
      display.flex,
      overflow.scroll,
      div(
        cls := "p-4 text-xs",
        display.grid,
        gridTemplateColumns := "auto auto",
        div(
          contain := "inline-size",
          child <-- selectedResultSignal.map(r => if r.isDefined then renderInfo() else emptyNode)
        ),
        div(
          display.flex,
          flexDirection.row,
          width.percent(100),
          marginBottom.px(ResultLines.rowHeight / 4),
          children <-- stagesSignal.map(stages => stages.zipWithIndex.toSeq.map(renderStage))
        ),
        div(
          display.flex,
          flexDirection.column,
          height.percent(100),
          textAlign.right,
          children <-- driversSignal.map(drivers => drivers.sortBy(_.results.head.stagePosition).map(renderDriver))
        ),
        ResultLines(
          stagesSignal,
          driversSignal,
          selectedDriverSignal,
          driverSelectionBus,
          selectDriver,
          selectResult
        ).render()
      ),
      driverSelectionBus.events --> selectDriver
    )

  private def renderInfo() =
    div(
      cls := "p-4 text-xs border-2 bg-white",
      display.flex,
      flexDirection.column,
      children <-- (
        Signal
          .combine(selectedResultSignal, selectedDriverSignal, stagesSignal)
          .mapN { (maybeResult, maybeDriver, stages) =>
            (maybeResult, maybeDriver) match {
              case (Some(result), Some(driver)) =>
                Seq(
                  div(s"SS${result.stageNumber} ${stages(result.stageNumber - 1).name}"),
                  div(renderCountryAndName(driver)),
                  div(s"Stage: ${result.stageTime.prettyDiff} (${ResultLines.renderStagePosition(result)})"),
                  div(s"Rally: ${result.overallTime.prettyDiff} (${result.overallPosition})"),
                  if result.comment.nonEmpty then div(s"“${result.comment}”") else emptyNode
                )
              case _ => Seq(emptyNode)
            }
          }
      )
    )

  private def renderCountryAndName(driver: Driver) =
    val (flag, countryName) =
      if driver.country == "RU" then ("🌻", "Slava Ukraini!")
      else
        (
          countryEmoji.mod.flag(driver.country).toOption.getOrElse("🏴"),
          countryEmoji.mod.name(driver.country).toOption.getOrElse("???")
        )
    val id = Random.nextString(5)

    Seq(
      span(
        dataAttr("tooltip-target") := s"driver-flag-$id",
        flag + " "
      ),
      div(
        idAttr := s"driver-flag-$id",
        role := "tooltip",
        cls := "absolute z-10 invisible inline-block px-3 py-2 text-xs text-white transition-opacity duration-300 bg-gray-900 rounded-lg shadow-sm opacity-0 tooltip dark:bg-gray-700",
        countryName,
        onMountCallback(ctx => initFlowbite())
      ),
      span(renderFullName(driver))
    )

  private def renderFullName(driver: Driver) =
    val parts = driver.userName :: (if driver.realName.nonEmpty then driver.realName :: Nil else Nil)
    parts.mkString(" / ")

  private def renderDriver(driverResults: DriverResults) =
    div(
      cls := "clickable",
      marginTop.auto,
      marginBottom.auto,
      whiteSpace.nowrap,
      renderCountryAndName(driverResults.driver),
      onClick.map(_ => driverResults.driver) --> driverSelectionBus.writer,
      opacity <-- selectedDriverSignal.map(d =>
        d.map(d => if d == driverResults.driver then "1" else "0.2").getOrElse("1")
      )
    )

  private def renderStage(stage: Stage, idx: Int) =
    span(
      marginLeft.auto,
      marginRight.auto,
      writingMode := "vertical-lr",
      transform := "rotate(180deg)",
      zIndex := "-1",
      stage.name
    )
