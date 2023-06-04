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

import typings.flowbite.mod.initFlowbite

import com.raquo.laminar.api.L._
import rallyeye.shared.RallyData

object Components:
  def header(rallySignal: Signal[RallyData], filterSignal: Signal[String]) =
    navTag(
      cls := "bg-white border-gray-200 px-4 lg:px-6 py-2.5 shadow-lg",
      div(
        cls := "flex flex-wrap justify-between items-center mx-auto max-w-screen-xl",
        a(
          href := "https://rallyeye.2m.lt",
          cls := "flex items-center",
          img(src := "/rallyeye.svg", cls := "mr-3 h-6 sm:h-9", alt := "RallyEye logo"),
          span(cls := "self-center text-xl font-semibold whitespace-nowrap", "RallyEye")
        ),
        child <-- rallySignal.map { r =>
          div(
            a(
              href := s"https://www.rallysimfans.hu/rbr/rally_online.php?centerbox=rally_list_details.php&rally_id=${r.id}",
              target := "_blank",
              r.name
            ),
            p(cls := "text-xs text-gray-400", "Data retrieved at ", r.retrievedAt.toString)
          )
        },
        children <-- rallySignal.combineWith(filterSignal).map(ResultFilter.render),
        div(
          cls := "flex items-center lg:order-2",
          a(
            href := "https://github.com/2m/rallyeye/",
            target := "_blank",
            cls := "text-gray-800 hover:bg-gray-50 focus:ring-4 focus:ring-gray-300 font-medium rounded-lg text-sm px-4 lg:px-5 py-2 lg:py-2.5 mr-2 focus:outline-none",
            "GitHub"
          )
        )
      )
    )

object ResultFilter:
  val AllResults = "All Results"
  val AllResultsId = filterId(AllResults)

  case class ResultFilter(name: String, group: String, isGroup: Boolean = false, isCar: Boolean = false) {
    def id = filterId(name)
  }

  def filters(rallyData: RallyData) =
    (List(ResultFilter(AllResults, AllResults)) :++ rallyData.groupResults
      .map(r => ResultFilter(r.group, r.group, isGroup = true))
      :++ rallyData.carResults
        .map(r => ResultFilter(r.car, r.group, isCar = true)))
      .map(rf => rf.id -> rf)
      .toMap

  def entries(rallyData: RallyData) =
    Map(filterId(AllResults) -> rallyData.allResults) ++ rallyData.groupResults.map(r =>
      filterId(r.group) -> r.results
    ) ++ rallyData.carResults.map(r => filterId(r.car) -> r.results)

  def filterId(name: String) =
    name.toLowerCase.replaceAll("[^a-z0-9]", "-")

  def render(rallyData: RallyData, filter: String) =
    val selected = filters(rallyData)(filter)
    Seq(
      button(
        cls := "text-white bg-gray-600 rounded-lg text-sm px-4 py-2.5 w-30 text-center inline-flex items-center",
        dataAttr("dropdown-toggle") := "dropdown",
        s"${selected.name} ▼"
      ),
      div(
        idAttr := "dropdown",
        cls := "hidden bg-white divide-y divide-gray-100 shadow w-30",
        ul(
          filters(rallyData).values.toSeq
            .sortBy(rf => (rf.group, rf.isCar))
            .map(rf =>
              li(
                a(
                  cls := "block px-4 py-2 hover:text-white hover:bg-gray-600",
                  if rf.id == filter then cls := "text-white bg-gray-600"
                  else if rf.isGroup then cls := "bg-gray-200"
                  else if rf.isCar then cls := "text-sm"
                  else emptyMod,
                  Router.navigateTo(Router.RallyPage(rallyData.id, rf.id)),
                  rf.name
                )
              )
            )
        ),
        onMountCallback(ctx => initFlowbite())
      )
    )
