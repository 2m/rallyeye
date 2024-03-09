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

object About:
  def render() =
    div(
      cls := "min-w-stretch w-fit shadow-inner",
      div(
        cls := "grid grid-cols-1 sm:grid-cols-1 gap-4 p-4 max-w-fit",
        margin := "0 auto",
        div(
          cls := "max-w-md",
          p(cls := "mt-2", "Welcome to RallyEye!"),
          p(
            cls := "mt-2",
            "Here you can find sim and real rally results visualized as interactive graphs. Such presentation makes it easier to follow results and recognize various stories that usually hide in the tables of stage times."
          ),
          p(
            cls := "mt-2",
            "Currently RallyEye supports all ",
            a(href := "https://www.rallysimfans.hu", target := "_blank", cls := "underline", "RallySimFans.hu"),
            " sim rallies, all ",
            a(href := "https://www.ewrc-results.com", target := "_blank", cls := "underline", "ewrc-results.com"),
            " real rallies and select ",
            a(href := "https://pressauto.lt/", target := "_blank", cls := "underline", "Press Auto"),
            " real rallies."
          ),
          p(
            cls := "mt-2",
            "On the first page there are some filters for select championships and rallies to quickly showcase what RallyEye can do."
          ),
          p(
            cls := "mt-2",
            "However you can view any ",
            a(
              href := "https://www.rallysimfans.hu/rbr/rally_online.php",
              target := "_blank",
              cls := "underline",
              "RallySimFans"
            ),
            " or ",
            a(
              href := "https://www.ewrc-results.com",
              target := "_blank",
              cls := "underline",
              "ewrc-results"
            ),
            " rally result in RallyEye."
          ),
          p(
            "For rally from RallySimFans, get the ",
            samp("rally_id"),
            " from the URL and use it like so: "
          ),
          samp("https://rallyeye.2m.lt/#/rsf/<rally_id>"),
          p(
            "For rally from ewrc-results.com, get the ",
            samp("rally_slug"),
            " from the URL and use it like so: "
          ),
          samp("https://rallyeye.2m.lt/#/ewrc/<rally_slug>"),
          p(
            cls := "mt-2",
            "When on the results stage, every rally stage is represented as a column and every rally driver as a row. The number in a circle shows the position driver took in that particular stage."
          ),
          p(
            cls := "mt-2",
            "You can click on any stage result circle to highlight that particular drivers journey throughout the rally. That also opens a popup window that shows stage and rally times at that point in the rally. Also a comment will show in the popup if a driver left one after the stage."
          ),
          p(
            cls := "mt-2",
            "Have fun exploring the rallies! And remember: flat to the square right!"
          ),
          p(
            cls := "mt-2",
            "Made with ❤️ in Vilnius 🇱🇹, Ko Pha Ngan 🇹🇭 and across the 🌍"
          )
        )
      )
    )
