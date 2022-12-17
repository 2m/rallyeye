package rallyeye

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import typings.d3Scale.mod.ScaleOrdinal_
import typings.d3Scale.mod.scaleLinear
import typings.d3Scale.mod.scaleOrdinal
import typings.d3ScaleChromatic.mod.schemeCategory10
import typings.d3Selection.mod.Selection_
import typings.d3Selection.mod.select
import typings.d3Shape.mod.line

import org.scalajs.dom
import org.scalajs.dom.HTMLElement

case class Stage(number: Int, name: String)
case class Result(stageNumber: Int, position: Int, overall: Int)
case class Driver(name: String, results: List[Result])

case class Margin(top: Int, right: Int, bottom: Int, left: Int)

object RallyEye:
  val width = 1000
  val height = 1100
  val margin = Margin(130, 30, 50, 130)

def xScale(stages: js.Array[Stage]) = scaleLinear()
  .domain(js.Array(0, stages.size - 1))
  .range(js.Array(RallyEye.margin.left, RallyEye.width - RallyEye.margin.right))

def yScale(drivers: js.Array[Driver]) = scaleLinear()
  .domain(js.Array(1, drivers.flatMap(_.results.map(_.overall)).max))
  .range(js.Array(RallyEye.margin.top, RallyEye.height - RallyEye.margin.bottom))

def colorScale(drivers: js.Array[Driver]) = scaleOrdinal(schemeCategory10).domain(drivers.map(d => d.name).toJSArray)

def positionColorScale = scaleOrdinal(js.Array(1, 2, 3), js.Array("#af9500", "#b4b4b4", "#6a3805"))
  .unknown("#000000")
  .asInstanceOf[ScaleOrdinal_[Int, String, Nothing]]

def xAxis(selection: Selection_[Nothing, Nothing, HTMLElement, Any], stages: js.Array[Stage]) =
  selection
    .style("font", "12px sans-serif")
    .selectAll("g")
    .data(stages)
    .join("g")
    .attr("transform", (_, d, idx, _) => s"translate(${xScale(stages)(idx)}, 0)")

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

def yAxis(selection: Selection_[Nothing, Nothing, HTMLElement, Any], drivers: js.Array[Driver]) =
  selection
    .style("font", "12px sans-serif")
    .selectAll("g")
    .data(drivers)
    .join("g")
    .attr("transform", (_, d, _, _) => s"translate(0, ${yScale(drivers)(d.results(0).overall)})")
    .call((g, _) => g.append("text").attr("dy", "0.4em").text((_, d, _, _) => d.name))

def resultLines(
    selection: Selection_[Nothing, Nothing, HTMLElement, Any],
    stages: js.Array[Stage],
    drivers: js.Array[Driver]
) = {
  def mkLine = line[Result]().x((r, idx, _) => xScale(stages)(idx)).y((r, _, _) => yScale(drivers)(r.overall))

  val result = selection
    .attr("stroke-width", 1.5)
    .selectAll("g")
    .data(drivers)
    .join("g")

  // result line
  result
    .append("path")
    .attr("fill", "none")
    .attr("stroke", (_, d, _, _) => colorScale(drivers)(d.name))
    .attr("d", (_, d, _, _) => mkLine(d.results.toJSArray))

  // result circle
  result
    .append("g")
    .attr("stroke", "white")
    .selectAll("circle")
    .data((_, d, _, _) => d.results.toJSArray)
    .join("circle")
    .attr("fill", (_, d, _, _) => positionColorScale(d.position).asInstanceOf[String])
    .attr("transform", (_, d, idx, _) => s"translate(${xScale(stages)(idx)},${yScale(drivers)(d.overall)})")
    .attr("r", 12)

  // result number
  result
    .append("g")
    .attr("stroke-width", 1)
    .attr("stroke", "white")
    .style("font", "14px sans-serif")
    .attr("fill", "white")
    .selectAll("text")
    .data((_, d, _, _) => d.results.toJSArray)
    .join("text")
    .attr("transform", (_, d, idx, _) => s"translate(${xScale(stages)(idx)},${yScale(drivers)(d.overall)})")
    .text((_, d, _, _) => d.position)
    .attr("dy", "0.35em")
    .attr("dx", "-0.275em")
}

@main
def main(): Unit =
  val dimentions = js.Array[String | Double](0, 0, RallyEye.width, RallyEye.height)
  val chart = select("#app").append("svg").attr("viewbox", dimentions)

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  fetch().map { results =>
    val stages = getStages(results).toJSArray
    val drivers = getDrivers(results).toJSArray
    println(drivers)

    xAxis(chart.append("g"), stages)
    yAxis(chart.append("g"), drivers)
    resultLines(chart.append("g"), stages, drivers)
  }
