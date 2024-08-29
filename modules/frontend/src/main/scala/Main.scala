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
import com.raquo.laminar.api.*
import com.raquo.laminar.api.L.*
import components.About
import components.Alert
import components.Header
import components.RallyList
import components.RallyResult
import components.ResultFilter
import components.Sidebar
import org.scalajs.dom
import rallyeye.shared.*

@main
def main() =
  renderOnDomContentLoaded(dom.document.querySelector("#app"), App.app)

object App:
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
      val rallyKindAndId = Router.router.currentPageSignal.now() match
        case Router.RallyPage(rallyId, _) => Some(RallyKind.Rsf, rallyId)
        case Router.PressAuto(year, _)    => Some(RallyKind.PressAuto, year)
        case Router.Ewrc(rallyId, _)      => Some(RallyKind.Ewrc, rallyId)
        case Router.IndexPage             => None
        case Router.AboutPage             => None
        case _: Router.FindPage           => None
        case Router.FreshRallyPage        => None

      rallyKindAndId.foreach(fetchAndSetData(refresh = true))
  )

  val rallyList = Var(List.empty[RallySummary])
  val rallyListSignal = rallyList.signal

  val rallyListFilter = Var(Option.empty[RallyList.Filter])
  val rallyListFilterSignal = rallyListFilter.signal

  val sidebarVisible = Var(false)

  import Router.*
  val app = div(
    child <-- router.currentPageSignal.map(renderPage)
  )

  def fetchData(kind: RallyKind, rallyId: String, refresh: Boolean) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    Var.set(
      App.loading -> true,
      App.errorInfo -> None,
      App.rallyData -> None,
      App.selectedDriver -> None,
      App.selectedResult -> None
    )

    val endpoint = if refresh then Endpoints.refresh else Endpoints.data
    val response = fetch((kind, rallyId), endpoint).flatMap {
      case response @ Left(RallyNotStored()) => fetch((kind, rallyId), Endpoints.refresh)
      case response                          => Future.successful(response)
    }
    response.onComplete(_ => Var.set(App.loading -> false))
    response

  def fetchRallyList(kind: RallyKind, championship: String, year: Option[Int]) =
    fetch((kind, championship, year), Endpoints.find)

  def fetchFreshRallies() =
    fetch((), Endpoints.fresh)

  def renderPage(page: Page) =
    page match
      case IndexPage =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        indexPage()
      case AboutPage =>
        Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None)
        aboutPage()
      case FindPage(filter)            => renderFindPage(filter)
      case FreshRallyPage              => renderFindPage(RallyList.Fresh)
      case RallyPage(rallyId, results) => renderRally(RallyKind.Rsf, rallyId, results)
      case PressAuto(year, results)    => renderRally(RallyKind.PressAuto, year, results)
      case Ewrc(rallyId, results)      => renderRally(RallyKind.Ewrc, rallyId, results)

  def renderRally(kind: RallyKind, rallyId: String, resFilter: String) =
    rallyData
      .now()
      .map(_.id)
      .orElse(Some(""))
      .filter(_ != rallyId)
      .foreach(_ => fetchAndSetData(refresh = false)(kind, rallyId))
    Var.set(resultFilter -> resFilter, sidebarVisible -> false)
    rallyPage()

  def renderFindPage(filter: RallyList.Filter) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    Var.set(App.rallyData -> None, App.selectedDriver -> None, App.selectedResult -> None, sidebarVisible -> false)

    val data = filter match
      case RallyList.Championship(kind, championship, year) => fetchRallyList(kind, championship, year)
      case RallyList.Fresh                                  => fetchFreshRallies()

    val _ = data.map {
      case Right(rallyList) => Var.set(App.rallyList -> rallyList, App.rallyListFilter -> Some(filter))
      case Left(error)      => Var.set(App.errorInfo -> Some(error))
    }
    findPage()

  def fetchAndSetData(refresh: Boolean)(kind: RallyKind, rallyId: String) =
    import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.global
    fetchData(kind, rallyId, refresh).map {
      case Right(rallyData) => Var.set(App.rallyData -> Some(rallyData))
      case Left(error)      => Var.set(App.errorInfo -> Some(error))
    }

  def indexPage() =
    rallyListFilter
      .now()
      .getOrElse(RallyList.Fresh)
      .pipe:
        case c: RallyList.Championship => Router.FindPage(c)
        case RallyList.Fresh           => Router.FreshRallyPage
      .pipe(router.replaceState)
    div()

  def findPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal, sidebarVisible).render(),
      RallyList(rallyListSignal, rallyListFilterSignal).render()
    )

  def aboutPage() =
    div(
      Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal, sidebarVisible).render(),
      About.render()
    )

  def rallyPage() =
    div(
      Sidebar(rallyDataSignal, resultFilter.signal, sidebarVisible).render(),
      div(
        cls := "sm:ml-64",
        Header(rallyDataSignal, resultFilter.signal, refreshData, loadingSignal, sidebarVisible).render(),
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
    )
