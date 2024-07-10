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
import rallyeye.Router
import rallyeye.shared.RallyData

object ResultFilter:
  val AllResults = "All Drivers"
  val AllResultsId = filterId(AllResults)

  case class ResultFilter(
      name: String,
      group: String,
      isGroup: Boolean = false,
      isCar: Boolean = false,
      order: Int = 1
  ):
    def id = if isCar then filterId(s"$group|$name") else filterId(name)

  private def filters(rallyData: RallyData) =
    (List(ResultFilter(AllResults, AllResults, order = 0)) :++
      rallyData.groupResults.map(r => ResultFilter(r.group, r.group, isGroup = true)) :++
      rallyData.carResults.map(r => ResultFilter(r.car, r.group, isCar = true)))
      .map(rf => rf.id -> rf)
      .toMap

  def entries(rallyData: RallyData) =
    Map(filterId(AllResults) -> rallyData.allResults) ++
      rallyData.groupResults.map(r => filterId(r.group) -> r.results) ++
      rallyData.carResults.map(r => filterId(s"${r.group}|${r.car}") -> r.results)

  def filterId(name: String) =
    name.toLowerCase.replaceAll("[^|a-z0-9]", "-")

  def render(rallyData: RallyData, filter: String) =
    filters(rallyData).values.toSeq
      .sortBy(rf => (rf.order, rf.group, rf.isCar, rf.name))
      .map: rf =>
        li(
          a(
            cls := "rounded-lg block mx-2 px-2 py-2 hover:text-white hover:bg-gray-600",
            cls("text-white bg-gray-600") := rf.id == filter,
            cls("bg-gray-200") := rf.isGroup,
            cls("text-sm") := rf.isCar,
            Router.navigateTo(Router.withFilter(rf.id)),
            rf.name
          )
        )
