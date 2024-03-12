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
import scala.util.chaining.*

import com.raquo.airstream.core.{Observer, Signal}
import com.raquo.airstream.state.Var
import com.raquo.airstream.state.Var.apply
import com.raquo.laminar.api.*
import com.raquo.laminar.api.L.*
import components.About
import components.Alert
import components.Header
import components.RallyList
import components.RallyResult
import components.ResultFilter
import org.scalajs.dom
import rallyeye.shared.*

@main
def main() =
  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App:
  case class DataAndRefresh[Req, Resp](data: Endpoint[Req, Resp], refresh: Endpoint[Req, Resp])
  val RsfEndpoints = DataAndRefresh(Endpoints.Rsf.data, Endpoints.Rsf.refresh)
  val PressAutoEndpoints = DataAndRefresh(Endpoints.PressAuto.data, Endpoints.PressAuto.data)
  val EwrcEndpoints = DataAndRefresh(Endpoints.Ewrc.data, Endpoints.Ewrc.refresh)

  val loading = Var(false)
  val loadingSignal = loading.signal

  val errorInfo = Var(Option.empty[ErrorInfo])
  val errorInfoSignal = errorInfo.signal

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
      val rallyIdAndEndpoint = Router.router.currentPageSignal.now() match
        case Router.RallyPage(rallyId, _) => Some(rallyId, RsfEndpoints)
        case Router.PressAuto(year, _)    => Some(year, PressAutoEndpoints)
        case Router.Ewrc(rallyId, _)      => Some(rallyId, EwrcEndpoints)
        case Router.IndexPage             => None
        case Router.AboutPage             => None
        case _: Router.FindPage           => None

      rallyIdAndEndpoint.foreach(fetchAndSetData(refresh = true))
  )

  val rallyList = Var(List.empty[RallySummary])
  val rallyListSignal = rallyList.signal

  val rallyListFilter = Var(Option.empty[RallyList.Filter])
  val rallyListFilterSignal = rallyListFilter.signal

  import Router.*
  val app = div(
    child <-- router.currentPageSignal.map(renderPage)
  )

  def fetchData[Req, Resp](rallyId: Req, endpoints: DataAndRefresh[Req, Resp], refresh: Boolean) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    Var.set(
      App.loading -> true,
      App.errorInfo -> None,
      App.rallyData -> None,
      App.selectedDriver -> None,
      App.selectedResult -> None
    )

    val endpoint = if refresh then endpoints.refresh else endpoints.data
    val response = fetch(rallyId, endpoint).flatMap {
      case response @ Left(RallyNotStored()) => fetch(rallyId, endpoints.refresh)
      case response                          => Future.successful(response)
    }
    response.onComplete(_ => Var.set(App.loading -> false))
    response

  def fetchRallyList(kind: RallyKind, championship: String, year: Option[Int]) =
    fetch((kind, championship, year), Endpoints.find)

  def renderPage(page: Page) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    page match
      case IndexPage =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        indexPage()
      case AboutPage =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        aboutPage()
      case FindPage(filter @ RallyList.Filter(kind, championship, year)) =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        fetchRallyList(kind, championship, year).map {
          case Right(rallyList) => Var.set(App.rallyList -> rallyList, App.rallyListFilter -> Some(filter))
          case Left(error)      => Var.set(App.errorInfo -> Some(error))
        }
        findPage()
      case RallyPage(rallyId, results) => renderRally(rallyId, results, RsfEndpoints)
      case PressAuto(year, results)    => renderRally(year, results, PressAutoEndpoints)
      case Ewrc(rallyId, results)      => renderRally(rallyId, results, EwrcEndpoints)

  def renderRally[Req, Resp <: RallyData](rallyId: Req, resFilter: String, endpoints: DataAndRefresh[Req, Resp]) =
    rallyData
      .now()
      .map(_.id)
      .orElse(Some(""))
      .filter(_ != rallyId)
      .foreach(_ => fetchAndSetData(refresh = false)(rallyId, endpoints))
    Var.set(resultFilter -> resFilter)
    rallyPage()

  def fetchAndSetData[Req, Resp <: RallyData](refresh: Boolean)(rallyId: Req, endpoints: DataAndRefresh[Req, Resp]) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    fetchData(rallyId, endpoints, refresh).map {
      case Right(rallyData) => Var.set(App.rallyData -> Some(rallyData))
      case Left(error)      => Var.set(App.errorInfo -> Some(error))
    }

  def indexPage() =
    rallyListFilter.now().getOrElse(RallyList.filters.head._2) pipe Router.FindPage.apply pipe router.replaceState
    div()

  def findPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal).render(),
      RallyList(rallyListSignal, rallyListFilterSignal).render()
    )

  def aboutPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal).render(),
      About.render()
    )

  def rallyPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal).render(),
      Alert(errorInfoSignal).render(),
      RallyResult(
        stagesSignal,
        driversSignal,
        selectedDriverSignal,
        selectDriver,
        selectResult,
        selectedResultSignal
      ).render()
    )
