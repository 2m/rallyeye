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

import com.raquo.laminar.api.L.*
import com.raquo.laminar.api.L.svg
import com.raquo.laminar.api.features.unitArrows
import rallyeye.*
import rallyeye.shared.RallyData

object Header:
  private def spinnerIcon =
    svg.svg(
      svg.cls := "h-10 text-gray-200 animate-spin",
      svg.viewBox := "0 0 100 101",
      svg.path(
        svg.fill := "currentColor",
        svg.d := "M100 50.5908C100 78.2051 77.6142 100.591 50 100.591C22.3858 100.591 0 78.2051 0 50.5908C0 22.9766 22.3858 0.59082 50 0.59082C77.6142 0.59082 100 22.9766 100 50.5908ZM9.08144 50.5908C9.08144 73.1895 27.4013 91.5094 50 91.5094C72.5987 91.5094 90.9186 73.1895 90.9186 50.5908C90.9186 27.9921 72.5987 9.67226 50 9.67226C27.4013 9.67226 9.08144 27.9921 9.08144 50.5908Z"
      ),
      svg.path(
        svg.fill := "currentFill",
        svg.d := "M93.9676 39.0409C96.393 38.4038 97.8624 35.9116 97.0079 33.5539C95.2932 28.8227 92.871 24.3692 89.8167 20.348C85.8452 15.1192 80.8826 10.7238 75.2124 7.41289C69.5422 4.10194 63.2754 1.94025 56.7698 1.05124C51.7666 0.367541 46.6976 0.446843 41.7345 1.27873C39.2613 1.69328 37.813 4.19778 38.4501 6.62326C39.0873 9.04874 41.5694 10.4717 44.0505 10.1071C47.8511 9.54855 51.7191 9.52689 55.5402 10.0491C60.8642 10.7766 65.9928 12.5457 70.6331 15.2552C75.2735 17.9648 79.3347 21.5619 82.5849 25.841C84.9175 28.9121 86.7997 32.2913 88.1811 35.8758C89.083 38.2158 91.5421 39.6781 93.9676 39.0409Z"
      )
    )

case class Header(
    rallySignal: Signal[Option[RallyData]],
    filterSignal: Signal[String],
    refreshData: Observer[Unit],
    loadingSignal: Signal[Boolean],
    sidebarVisible: Var[Boolean]
):
  import Header.*

  val refreshDataBus = EventBus[Unit]()

  def render() =
    navTag(
      cls := "bg-white border-gray-200 m-2",
      child <-- loadingSignal.map {
        case true =>
          div(
            cls := "flex justify-end",
            spinnerIcon
          )
        case false =>
          div(
            cls := "flex justify-between items-center max-w-screen-md mx-auto",
            children <-- rallySignal.map {
              case Some(r) =>
                Seq(
                  Common.sidebarToggle(sidebarVisible),
                  div(
                    cls := "mx-auto",
                    a(href := r.link, target := "_blank", r.name),
                    p(
                      cls := "text-xs text-gray-400",
                      span("Data retrieved ", r.retrievedAt.prettyAgo, " "),
                      a(cls := "clickable", onClick.map(_ => ()) --> refreshDataBus.writer, "↻")
                    )
                  )
                )
              case None => Common.title
            }
          )
      },
      refreshDataBus.events --> refreshData
    )

case class Sidebar(
    rallySignal: Signal[Option[RallyData]],
    filterSignal: Signal[String],
    sidebarVisible: Var[Boolean]
):
  def render() =
    asideTag(
      idAttr := "sidebar",
      cls := "fixed top-0 left-0 z-40 w-64 h-screen transition-transform -translate-x-full sm:translate-x-0",
      cls <-- sidebarVisible.signal.map(if _ then "translate-x-0" else "-translate-x-full"),
      div(
        cls := "h-full overflow-y-auto bg-gray-50",
        ul(
          cls := "space-y-2 font-medium",
          li(
            cls := "flex justify-between m-2",
            Common.sidebarToggle(sidebarVisible),
            Common.title
          ),
          children <-- rallySignal.combineWith(filterSignal).map {
            case (Some(rally), filter) =>
              ResultFilter.render(rally, filter)
            case (None, _) => Seq(emptyNode)
          }
        )
      )
    )

object Common:
  def title =
    Seq(
      a(
        Router.navigateTo(Router.IndexPage),
        cls := "flex items-center",
        img(src := "/rallyeye.svg", cls := "h-10", alt := "RallyEye logo"),
        span(cls := "ml-1 self-center text-xl font-semibold whitespace-nowrap", "RallyEye")
      ),
      div(
        cls := "flex items-center",
        a(
          Router.navigateTo(Router.AboutPage),
          img(src := "/about.svg", cls := "h-8 p-1", alt := "Question mark")
        ),
        a(
          href := "https://github.com/2m/rallyeye/",
          target := "_blank",
          img(src := "/github.svg", cls := "h-8 p-1", alt := "GitHub logo")
        )
      )
    )

  def sidebarToggle(sidebarVisible: Var[Boolean]) =
    button(
      typ := "button",
      cls := "inline-flex items-center p-1 text-sm text-gray-500 rounded-lg sm:hidden hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-gray-200",
      svg.svg(
        svg.cls := "w-8 h-8",
        svg.viewBox := "0 0 20 20",
        svg.path(
          svg.d := "M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zm0 10.5a.75.75 0 01.75-.75h7.5a.75.75 0 010 1.5h-7.5a.75.75 0 01-.75-.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10z"
        )
      ),
      onClick --> sidebarVisible.update(!_)
    )
