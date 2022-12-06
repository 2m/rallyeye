package rallyeye

import org.scalajs.dom
import typings.d3Selection.mod.select
import typings.d3Scale.mod.scaleLinear
import typings.d3Scale.mod.scaleOrdinal
import typings.d3Scale.mod.ScaleOrdinal_
import typings.d3Selection.mod.Selection_
import typings.d3ScaleChromatic.mod.schemeCategory10
import typings.d3Shape.mod.line
import org.scalajs.dom.HTMLElement
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

case class Stage(name: String, distance: Int)
case class Result(stage: Stage, position: Int, overall: Int)
case class Driver(name: String, results: List[Result])

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  val width = 800
  val height = 600
  val margin = Margin(50, 30, 50, 50)

  val stages = List(Stage("SS1", 0), Stage("SS2", 1), Stage("SS3", 2), Stage("SS4", 3))

  val data = List(
    Driver("Kalle", List(Result(stages(0), 1, 1), Result(stages(1), 2, 1), Result(stages(2), 2, 2))),
    Driver("Ott", List(Result(stages(0), 2, 2), Result(stages(1), 1, 2), Result(stages(2), 1, 1))),
    Driver("Taka", List(Result(stages(0), 3, 3), Result(stages(1), 3, 3), Result(stages(2), 3, 3)))
  )

def xScale = scaleLinear()
  .domain(js.Array(0, RallyEye.stages.size - 1))
  .range(js.Array(RallyEye.margin.left, RallyEye.width - RallyEye.margin.right))

def yScale = scaleLinear()
  .domain(js.Array(1, RallyEye.data.size))
  .range(js.Array(RallyEye.margin.top, RallyEye.height - RallyEye.margin.bottom))

def colorScale = scaleOrdinal(schemeCategory10).domain(RallyEye.data.map(d => d.name).toJSArray)

def positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
  .unknown("#000000")
  .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

def xAxis(selection: Selection_[Nothing, Nothing, HTMLElement, Any]) =
  selection
    .style("font", "12px sans-serif")
    .selectAll("g")
    .data(RallyEye.stages.toJSArray)
    .join("g")
    .attr("transform", (_, d, _, _) => s"translate(${xScale(d.distance)}, 0)")

    // top stage name
    .call((g, _) =>
      g.append("text")
        .attr("transform", s"translate(0,${RallyEye.margin.top}) rotate(-90)")
        .attr("x", 20)
        .attr("dy", "0.35em")
        .text((_, d, _, _) => d.name)
    )
    // bottom stage name
    .call((g, _) =>
      g.append("text")
        .attr("text-anchor", "end")
        .attr("transform", s"translate(0, ${RallyEye.height - RallyEye.margin.top}) rotate(-90)")
        .attr("x", -20)
        .attr("dy", "0.35em")
        .text((_, d, _, _) => d.name)
    )

def yAxis(selection: Selection_[Nothing, Nothing, HTMLElement, Any]) =
  selection
    .style("font", "12px sans-serif")
    .selectAll("g")
    .data(RallyEye.data.toJSArray)
    .join("g")
    .attr("transform", (_, d, _, _) => s"translate(0, ${yScale(d.results(0).overall)})")
    .call((g, _) => g.append("text").attr("dy", "0.4em").text((_, d, _, _) => d.name))

def resultLines(selection: Selection_[Nothing, Nothing, HTMLElement, Any]) = {
  def mkLine = line[Result]().x((r, _, _) => xScale(r.stage.distance)).y((r, _, _) => yScale(r.overall))

  val result = selection
    .attr("stroke-width", 1.5)
    .selectAll("g")
    .data(RallyEye.data.toJSArray)
    .join("g")

  result
    .append("path")
    .attr("fill", "none")
    .attr("stroke", (_, d, _, _) => colorScale(d.name))
    .attr("d", (_, d, _, _) => mkLine(d.results.toJSArray))

  result
    .append("g")
    .attr("stroke", "white")
    .selectAll("circle")
    .data((_, d, _, _) => d.results.toJSArray)
    .join("circle")
    .attr("fill", (_, d, _, _) => positionColorScale(d.position).asInstanceOf[String])
    .attr("transform", (_, d, _, _) => s"translate(${xScale(d.stage.distance)},${yScale(d.overall)})")
    .attr("r", 12)

  result
    .append("g")
    .attr("stroke-width", 1)
    .attr("stroke", "white")
    .style("font", "14px sans-serif")
    .attr("fill", "white")
    .selectAll("text")
    .data((_, d, _, _) => d.results.toJSArray)
    .join("text")
    .attr("transform", (_, d, _, _) => s"translate(${xScale(d.stage.distance)},${yScale(d.overall)})")
    .text((_, d, _, _) => d.position)
    .attr("dy", "0.35em")
    .attr("dx", "-0.275em")
}

@main
def main(): Unit =
  val dimentions = js.Array[String | Double](0, 0, RallyEye.width, RallyEye.height)
  val chart = select("#app").append("svg").attr("viewbox", dimentions)

  xAxis(chart.append("g"))
  yAxis(chart.append("g"))
  resultLines(chart.append("g"))
