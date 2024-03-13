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
import rallyeye.*
import rallyeye.Router
import rallyeye.shared.RallyKind
import rallyeye.shared.RallySummary

case class RallyList(
    rallyListSignal: Signal[List[RallySummary]],
    rallyListFilterSignal: Signal[Option[RallyList.Filter]]
):
  import RallyList.*

  def render() =
    div(
      cls := "shadow-inner",
      div(
        cls := "max-w-screen-md grid grid-cols-1 sm:grid-cols-5 gap-0 sm:gap-4 p-4",
        margin := "0 auto",
        ul(
          cls := "col-span-2 flex-column space-y space-y-4 text-sm font-medium text-gray-500 mb-4",
          whiteSpace.nowrap,
          filters.map(renderFilter).toList
        ),
        div(
          cls := "col-span-3 flex flex-col",
          children <-- Signal
            .combine(rallyListSignal, rallyListFilterSignal)
            .mapN((rallyList, rallyFilter) => rallyList.sortBy(_.start).reverse.map(renderRally(rallyFilter)))
        )
      )
    )

  private def renderFilter(group: String, filter: Filter) =
    li(
      a(
        cls := "inline-flex items-center px-4 py-3 rounded-lg active w-full",
        cls <-- rallyListFilterSignal.map {
          case Some(f) if f == filter => "bg-gray-500 text-white"
          case _                      => "bg-gray-200 text-gray"
        },
        Router.navigateTo(Router.FindPage(RallyList.Filter(filter.kind, filter.championship, filter.year))),
        group
      )
    )

  private def renderRally(rallyFilter: Option[RallyList.Filter])(r: RallySummary) =
    a(
      cls := "block mb-4 bg-white border border-gray-200 rounded-lg shadow hover:bg-gray-100",
      Seq(
        renderRallyLink(r),
        div(
          cls := "flex flex-row",
          div(
            cls := "grow p-4",
            div(cls := "text-xl font-semibold text-gray-900", r.name),
            div(
              cls := "text-sm text-gray-500",
              rallyFilter.fold(emptyNode)(f => span(f.championship)),
              span(cls := "pl-2 text-xs text-gray-400", "data retrieved ", r.retrievedAt.prettyAgo, " ")
            ),
            div(cls := "mt-2 text-sm text-gray-500", s"${r.start.toString} - ${r.end.toString}"),
            div(
              cls := "mt-2 text-sm text-gray-500",
              s"Distance: ${r.distanceMeters / 1000} km, Started: ${r.started}, Finished: ${r.finished}"
            )
          ),
          div(
            cls := s"flex-none ${renderSourceColor(r)} font-bold text-white text-center p-2",
            RallyResult.writingMode := "vertical-lr",
            renderSourceName(r)
          )
        )
      )
    )

  private def renderRallyLink(info: RallySummary) =
    info.kind match
      case RallyKind.Rsf =>
        Router.navigateTo(Router.RallyPage(info.externalId, ResultFilter.AllResultsId))
      case RallyKind.PressAuto =>
        Router.navigateTo(Router.PressAuto(info.externalId, ResultFilter.AllResultsId))
      case RallyKind.Ewrc =>
        Router.navigateTo(Router.Ewrc(info.externalId, ResultFilter.AllResultsId))

  private def renderSourceName(info: RallySummary) =
    info.kind match
      case RallyKind.Rsf       => "rallysimfans"
      case RallyKind.PressAuto => "pressauto"
      case RallyKind.Ewrc      => "ewrc"

  private def renderSourceColor(info: RallySummary) =
    info.kind match
      case RallyKind.Rsf       => "bg-red-600"
      case RallyKind.PressAuto => "bg-lime-600"
      case RallyKind.Ewrc      => "bg-amber-600"

object RallyList:
  case class Filter(kind: RallyKind, championship: String, year: Option[Int] = None)
  val filters = List(
    "🌎 WRC 2024" -> Filter(RallyKind.Ewrc, "WRC", Some(2024)),
    "🌎 WRC 2023" -> Filter(RallyKind.Ewrc, "WRC", Some(2023)),
    "🖥️ Sim Rally Masters 2024" -> Filter(RallyKind.Rsf, "Sim Rally Masters 2024"),
    "🖥️ Sim Rally Masters 2023" -> Filter(RallyKind.Rsf, "Sim Rally Masters 2023"),
    "🖥️ Virtual Rally Championship 2024" -> Filter(RallyKind.Rsf, "Virtual Rally Championship 2024"),
    "🖥️ Virtual Rally Championship 2023" -> Filter(RallyKind.Rsf, "Virtual Rally Championship 2023"),
    "🇱🇹 Lithuania 2023" -> Filter(RallyKind.Ewrc, "Lithuania", Some(2023)),
    "🇱🇹 Press Auto" -> Filter(RallyKind.PressAuto, "Press Auto")
  )
