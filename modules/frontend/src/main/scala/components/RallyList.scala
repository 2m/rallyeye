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
        div(
          cls := "col-span-3 flex flex-col",
          children <-- Signal
            .combine(rallyListSignal, rallyListFilterSignal)
            .mapN: (rallyList, rallyFilter) =>
              given Ordering[RallySummary] = summaryOrdering(rallyFilter)
              rallyList.sorted.map(renderRally(rallyFilter))
        ),
        ul(
          cls := "col-span-2 flex-column space-y space-y-4 text-sm font-medium text-gray-500 mb-4",
          whiteSpace.nowrap,
          filters.map(renderFilter).toList
        )
      )
    )

  private def renderFilter(group: String, filters: List[(String, Filter)]) =
    li(
      cls := "px-4 py-2 rounded-lg active w-full bg-gray-200",
      cls <-- rallyListFilterSignal.map {
        case Some(f) if filters.map(_._2).contains(f) => "bg-gray-500 text-white"
        case _                                        => "bg-gray-200 text-gray"
      },
      span(
        cls := "inline-flex items-center",
        if group.nonEmpty then span(cls := "p-1", group) else emptyNode,
        filters.map: (name, filter) =>
          a(
            cls := "px-1 py-1 rounded-lg",
            cls <-- rallyListFilterSignal.map {
              case Some(f) if f == filter && group.nonEmpty => "outline-1 outline-white outline"
              case _                                        => ""
            },
            Router.navigateTo(filter match
              case c: Championship => Router.FindPage(c)
              case Fresh           => Router.FreshRallyPage),
            name
          )
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
              rallyFilter
                .flatMap:
                  case c: Championship                  => Some(c.championship)
                  case Fresh if r.championship.nonEmpty => Some(r.championship.mkString(", "))
                  case Fresh                            => None
                .fold(emptyNode)(span(cls := "pr-2", _)),
              span(cls := "text-xs text-gray-400 whitespace-nowrap", "data retrieved ", r.retrievedAt.prettyAgo, " ")
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

  private def summaryOrdering(rallyFilter: Option[RallyList.Filter]) =
    rallyFilter match
      case Some(Fresh) => Ordering.by((rs: RallySummary) => rs.retrievedAt).reverse
      case _           => Ordering.by((rs: RallySummary) => rs.start).reverse

object RallyList:
  sealed trait Filter
  case object Fresh extends Filter
  case class Championship(kind: RallyKind, championship: String, year: Option[Int] = None) extends Filter
  val filters: List[(String, List[(String, Filter)])] = List(
    "" -> List("🔄 Recently loaded rallies" -> Fresh),
    "🌎 WRC" -> List(
      "'24" -> Championship(RallyKind.Ewrc, "WRC", Some(2024)),
      "'23" -> Championship(RallyKind.Ewrc, "WRC", Some(2023))
    ),
    "🇪🇺 ERC" -> List(
      "'24" -> Championship(RallyKind.Ewrc, "ERC", Some(2024)),
      "'23" -> Championship(RallyKind.Ewrc, "ERC", Some(2023))
    ),
    "🖥️ Sim Rally Masters" -> List(
      "'24" -> Championship(RallyKind.Rsf, "Sim Rally Masters 2024"),
      "'23" -> Championship(RallyKind.Rsf, "Sim Rally Masters 2023")
    ),
    "🖥️ Virtual Rally Championship" -> List(
      "'24" -> Championship(RallyKind.Rsf, "Virtual Rally Championship 2024"),
      "'23" -> Championship(RallyKind.Rsf, "Virtual Rally Championship 2023")
    ),
    "🇺🇸 ARA Championship" -> List("'24" -> Championship(RallyKind.Ewrc, "ARA", Some(2024))),
    "🇱🇹 Lithuania" -> List(
      "'24" -> Championship(RallyKind.Ewrc, "Lithuania", Some(2024)),
      "'23" -> Championship(RallyKind.Ewrc, "Lithuania", Some(2023))
    ),
    "🇱🇹 Lithuania Rally Sprint" -> List(
      "'24" -> Championship(RallyKind.Ewrc, "Lithuania Rally Sprint", Some(2024)),
      "'23" -> Championship(RallyKind.Ewrc, "Lithuania Rally Sprint", Some(2023))
    ),
    "🇱🇹 Lithuania Minirally" -> List(
      "'24" -> Championship(RallyKind.Ewrc, "Lithuania Minirally", Some(2024)),
      "'23" -> Championship(RallyKind.Ewrc, "Lithuania Minirally", Some(2023))
    ),
    "" -> List("🇱🇹 Press Auto" -> Championship(RallyKind.PressAuto, "Press Auto"))
  )
