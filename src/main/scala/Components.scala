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

import com.raquo.laminar.api.L._

object Components:
  def header(rallyNameSignal: Signal[String]) =
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
        child <-- rallyNameSignal.map(div(_)),
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
