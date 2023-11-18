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

import scala.concurrent.Future

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.state.Var
import com.raquo.airstream.state.Var.apply
import com.raquo.laminar.api._
import com.raquo.laminar.api.L._
import components.Header
import components.RallyList
import components.RallyResult
import components.ResultFilter
import org.scalajs.dom
import rallyeye.shared._

@main
def main() =
  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App:
  case class DataAndRefresh(data: Endpoint, refresh: Option[Endpoint])
  val RsfEndpoints = DataAndRefresh(Endpoints.Rsf.data, Some(Endpoints.Rsf.refresh))
  val PressAutoEndpoints = DataAndRefresh(Endpoints.PressAuto.data, None)

  val loading = Var(false)
  val loadingSignal = loading.signal

  val rallyData = Var(Option.empty[RallyData])
  val rallyDataSignal = rallyData.signal
  val resultFilter = Var(ResultFilter.AllResultsId)

  val stagesSignal = rallyDataSignal.map(_.map(_.stages))
  val driversSignal =
    rallyDataSignal
      .combineWith(resultFilter.signal)
      .map((data, filter) => data.map(ResultFilter.entries).flatMap(_.get(filter)))

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

  val refreshData = Observer[Unit](
    onNext = _ =>
      val rallyIdAndEndpoint = Router.router.currentPageSignal.now() match {
        case Router.RallyPage(rallyId, _) => Some(rallyId, RsfEndpoints)
        case Router.PressAuto(year, _)    => Some(year, PressAutoEndpoints)
        case _                            => None
      }

      rallyIdAndEndpoint.foreach { (rallyId, endpoint) =>
        fetchData(rallyId, endpoint, true)
      }
  )

  import Router._
  val app = div(
    child <-- router.currentPageSignal.map(renderPage)
  )

  def fetchData(rallyId: Int, endpoints: DataAndRefresh, refresh: Boolean) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    Var.set(App.loading -> true, App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)

    val endpoint = if refresh && endpoints.refresh.isDefined then endpoints.refresh.get else endpoints.data
    val response = fetch(rallyId, endpoint).flatMap {
      case response @ Left(RallyNotStored()) =>
        endpoints.refresh match {
          case Some(refresh) => fetch(rallyId, refresh)
          case None          => Future.successful(response)
        }
      case response => Future.successful(response)
    }
    response.map {
      case Right(rallyData) =>
        Var.set(
          App.loading -> false,
          App.rallyData -> Some(rallyData)
        )
      case Left(error) => println(error)
    }

    ()

  def renderPage(page: Page) =
    page match {
      case IndexPage =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        indexPage()
      case RallyPage(rallyId, results) =>
        rallyData
          .now()
          .map(_.id)
          .orElse(Some(0))
          .filter(_ != rallyId)
          .foreach(_ => fetchData(rallyId, RsfEndpoints, false))
        Var.set(resultFilter -> results)
        rallyPage()
      case PressAuto(year, results) =>
        rallyData
          .now()
          .map(_.id)
          .orElse(Some(0))
          .filter(_ != year)
          .foreach(_ => fetchData(year, PressAutoEndpoints, false))
        Var.set(resultFilter -> results)
        rallyPage()
    }

  def indexPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal).render(),
      RallyList.render()
    )

  def rallyPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal).render(),
      RallyResult(
        stagesSignal,
        driversSignal,
        selectedDriverSignal,
        selectDriver,
        selectResult,
        selectedResultSignal
      ).render()
    )
