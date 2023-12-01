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

object RallyList:
  sealed trait Rally:
    val finish: String
  case class Rsf(
      id: String,
      name: String,
      championship: String,
      started: Int,
      finished: Int,
      distance: BigDecimal,
      start: String,
      finish: String
  ) extends Rally
  case class PressAuto(year: String, start: String, finish: String) extends Rally

  val showcase = List(
    Rsf(
      "60870",
      "#6 Tulip Rally",
      "Sim Rally Masters 2023",
      472,
      128,
      BigDecimal("367.0"),
      "2023-10-04",
      "2023-10-17"
    ),
    Rsf(
      "59430",
      "#5 Rallye Yukon Puebla",
      "Sim Rally Masters 2023",
      611,
      210,
      BigDecimal("547.5"),
      "2023-08-23",
      "2023-09-05"
    ),
    Rsf(
      "58147",
      "#4 RACC Rallye Catalunya",
      "Sim Rally Masters 2023",
      538,
      239,
      BigDecimal("256.5"),
      "2023-07-12",
      "2023-07-25"
    ),
    Rsf(
      "56098",
      "#3 Romanian Winter Rally",
      "Sim Rally Masters 2023",
      779,
      359,
      BigDecimal("285.3"),
      "2023-05-10",
      "2023-05-24"
    ),
    Rsf(
      "54067",
      "#2 International Castrol Rally",
      "Sim Rally Masters 2023",
      988,
      391,
      BigDecimal("332.9"),
      "2023-03-22",
      "2023-04-04"
    ),
    Rsf(
      "52325",
      "#1 Circuit of Ireland",
      "Sim Rally Masters 2023",
      1039,
      543,
      BigDecimal("256.1"),
      "2023-02-08",
      "2023-02-21"
    ),
    Rsf(
      "60569",
      "#10 Rally Greece",
      "Virtual Rally Championship 2023",
      367,
      149,
      BigDecimal("113.7"),
      "2023-09-25",
      "2023-10-01"
    ),
    Rsf(
      "59862",
      "#9 Barum Czech Rally Zlin",
      "Virtual Rally Championship 2023",
      512,
      250,
      BigDecimal("112.8"),
      "2023-09-04",
      "2023-09-10"
    ),
    Rsf(
      "59148",
      "#8 Rally Finland",
      "Virtual Rally Championship 2023",
      445,
      170,
      BigDecimal("160.5"),
      "2023-08-14",
      "2023-08-20"
    ),
    Rsf(
      "57878",
      "#7 Safari Rally Kenya",
      "Virtual Rally Championship 2023",
      451,
      173,
      BigDecimal("162.4"),
      "2023-07-03",
      "2023-07-09"
    ),
    Rsf(
      "57275",
      "#6 Rally Poland",
      "Virtual Rally Championship 2023",
      514,
      268,
      BigDecimal("111.2"),
      "2023-06-12",
      "2023-06-18"
    ),
    Rsf(
      "56613",
      "#5 Rally Islas Canarias",
      "Virtual Rally Championship 2023",
      514,
      263,
      BigDecimal("121.3"),
      "2023-05-24",
      "2023-05-30"
    ),
    Rsf(
      "55767",
      "#4 Rally Croatia",
      "Virtual Rally Championship 2023",
      656,
      305,
      BigDecimal("164.1"),
      "2023-05-01",
      "2023-05-07"
    ),
    Rsf(
      "53987",
      "#3 Rally Serras de Fafe",
      "Virtual Rally Championship 2023",
      726,
      420,
      BigDecimal("96.0"),
      "2023-03-20",
      "2023-03-26"
    ),
    Rsf(
      "52804",
      "#2 Rally Sweden",
      "Virtual Rally Championship 2023",
      829,
      435,
      BigDecimal("177.6"),
      "2023-02-20",
      "2023-02-26"
    ),
    Rsf(
      "51992",
      "#1 Rallye Automobile Monte-Carlo",
      "Virtual Rally Championship 2023",
      989,
      576,
      BigDecimal("158.7"),
      "2023-01-30",
      "2023-02-05"
    ),
    PressAuto("2023", "2023-06-16", "2023-06-17")
  )

  def render() =
    div(
      cls := "min-w-stretch w-fit shadow-inner",
      div(
        cls := "grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 max-w-fit",
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
            " sim rallies and select ",
            a(href := "https://pressauto.lt/", target := "_blank", cls := "underline", "Press Auto"),
            " real rallies."
          ),
          p(
            cls := "mt-2",
            "On this first page there are boxes for some select rallies to quickly showcase what RallyEye can do."
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
            " rally result by getting the ",
            samp("rally_id"),
            " from the URL and using it with the RallyEye like so: ",
            samp("https://rallyeye.2m.lt/#/rsf/<rally_id>")
          ),
          p(
            cls := "mt-2",
            "When on the results stage, every rally stage is represented as a column and every rally driver as a row. The number in a circle shows the position driver took in that particular stage."
          ),
          p(
            cls := "mt-2",
            "You can click on any stage result circle to highligh that particular drivers journey throughout the rally. That also opens a popup window that shows stage and rally times at that point in the rally. Also a comment will show in the popup if a driver left one after the stage."
          ),
          p(
            cls := "mt-2",
            "Have fun exploring the rallies! And remember: flat to the square right!"
          ),
          p(
            cls := "mt-2",
            "Made with ❤️ in Vilnius 🇱🇹 and across the 🌍"
          )
        ),
        div(
          cls := "flex flex-col justify-center",
          showcase.sortBy(_.finish).reverse.map(renderRally)
        )
      )
    )

  private def renderRally(rally: Rally) =
    a(
      cls := "block mt-4 bg-white border border-gray-200 rounded-lg shadow hover:bg-gray-100",
      rally match
        case r: Rsf =>
          Seq(
            Router.navigateTo(Router.RallyPage(r.id, ResultFilter.AllResultsId)),
            div(
              cls := "flex flex-row",
              div(
                cls := "grow p-4",
                div(cls := "text-xl font-semibold text-gray-900", r.name),
                div(cls := "text-sm text-gray-500", r.championship),
                div(cls := "mt-2 text-sm text-gray-500", s"${r.start} - ${r.finish}"),
                div(
                  cls := "mt-2 text-sm text-gray-500",
                  s"Distance: ${r.distance} km, S/F: ${r.started}/${r.finished}"
                )
              ),
              div(
                cls := "flex-none bg-gray-600 font-bold text-white text-center p-2",
                RallyResult.writingMode := "vertical-lr",
                "rallysimfans"
              )
            )
          )
        case r: PressAuto =>
          Seq(
            Router.navigateTo(Router.PressAuto(r.year, ResultFilter.AllResultsId)),
            div(
              cls := "flex flex-row",
              div(
                cls := "grow p-4",
                div(cls := "text-xl font-semibold text-gray-900", s"Press Auto ${r.year}"),
                div(cls := "mt-2 text-sm text-gray-500", s"${r.start} - ${r.finish}")
              ),
              div(
                cls := "flex-none bg-gray-600 font-bold text-white text-center p-2",
                RallyResult.writingMode := "vertical-lr",
                "pressauto"
              )
            )
          )
    )
