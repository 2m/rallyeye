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

import typings.flowbite.mod.initDropdowns

import com.raquo.laminar.api.L._
import rallyeye.Router
import rallyeye.shared.RallyData

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
                  Router.navigateTo(Router.withFilter(rf.id)),
                  rf.name
                )
              )
            )
        ),
        onMountCallback(ctx => initDropdowns())
      )
    )
