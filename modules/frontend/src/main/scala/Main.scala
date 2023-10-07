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

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.state.Var
import com.raquo.airstream.state.Var.apply
import com.raquo.laminar.api._
import com.raquo.laminar.api.L._
import components.Header
import components.RallyResult
import components.ResultFilter
import org.scalajs.dom
import rallyeye.shared._

@main
def main() =
  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App {

  val rallyData = Var(RallyData.empty)
  val rallyDataSignal = rallyData.signal
  val resultFilter = Var(ResultFilter.AllResultsId)

  val stagesSignal = rallyDataSignal.map(_.stages)
  val driversSignal =
    rallyDataSignal.combineWith(resultFilter.signal).map((data, filter) => ResultFilter.entries(data)(filter))

  val selectedDriver = Var(Option.empty[Driver])
  val selectedDriverSignal = selectedDriver.signal
  val selectDriver = Observer[Driver](
    onNext = driver =>
      Var.set(
        selectedDriver -> Some(driver),
        selectedResult -> None
      )
  )

  val selectedResult = Var(Option.empty[DriverResult])
  val selectedResultSignal = selectedResult.signal
  val selectResult = selectedResult.someWriter

  import Router._
  val app = div(
    child <-- router.currentPageSignal.map(renderPage)
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
        Var.set(resultFilter -> results)
        rallyPage()
      case PressAuto(year, results) =>
        if rallyData.now().id != year then fetchData(year, pressAutoEndpoint)
        Var.set(resultFilter -> results)
        rallyPage()
    }

  def indexPage() =
    router.replaceState(RallyPage(48272, ResultFilter.AllResultsId)) // rally to show by default
    div()

  def rallyPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal),
      RallyResult(
        stagesSignal,
        driversSignal,
        selectedDriverSignal,
        selectDriver,
        selectResult,
        selectedResultSignal
      ).render()
    )

}
