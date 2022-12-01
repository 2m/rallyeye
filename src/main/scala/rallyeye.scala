package lt.dvim.rallyeye

import typings.std.global.console
import typings.d3Selection.mod.select
import typings.d3Scale.mod.scaleLinear
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import typings.d3Selection.mod.Selection_
import org.scalajs.dom.HTMLElement
import slinky.web.html.poster

case class Stage(name: String, distance: Int)
case class Result(stage: Stage, position: Int)
case class Driver(name: String, results: List[Result])

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  val width = 800
  val height = 600
  val margin = Margin(30, 30, 30, 50)

  val stages = List(Stage("SS1", 0), Stage("SS2", 1), Stage("SS3", 2), Stage("SS4", 3))

  val data = List(
    Driver("Kalle", List(Result(stages(0), 1), Result(stages(1), 1), Result(stages(2), 2))),
    Driver("Ott", List(Result(stages(0), 2), Result(stages(1), 2), Result(stages(2), 1)))
  )

/* yScale = scaleLinear()
  .domain([1, data.length])
  .range([margin.top, height - margin.bottom]) */

def yScale = scaleLinear()
  .domain(js.Array(1, RallyEye.data.size))
  .range(js.Array(RallyEye.margin.top, RallyEye.height - RallyEye.margin.bottom))

def yAxis(selection: Selection_[Nothing, Nothing, HTMLElement, Any]) =
  /* selection.style("font", "10px sans-serif")
    .selectAll("g").data(data).join("g")
    .attr("transform", d => `translate(0, ${yScale(d.results[0].position)})`)
    .call(g => g.append("text")
      .attr("dy", "0.4em")
      .text(d => d.name)) */
  selection
    .style("font", "10px sans-serif")
    .selectAll("g")
    .data(RallyEye.data.toJSArray)
    .attr("transform", (_, d, _, _) => s"translate(0, ${yScale(d.results(0).position)})")
    .join("g")
    .call((g, _) => g.append("text").attr("dy", "0.4em").text((_, d, _, _) => d.name))

@main
def main(): Unit =
  console.log("hello world")
  val dimentions = js.Array[String | Double](0, 0, RallyEye.width, RallyEye.height)
  val chart = select("#chart").append("svg").attr("viewbox", dimentions)

  yAxis(chart.append("g"))
