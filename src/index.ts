import { select, Selection } from "d3-selection"
import { scaleLinear, scaleOrdinal } from "d3-scale"
import { extent } from "d3-array"
import { line } from "d3-shape"
import { schemeCategory10 } from "d3-scale-chromatic"

const margin = ({ top: 30, right: 30, bottom: 30, left: 50 })
const width = 800
const height = 600

class Stage {
  constructor(readonly name: string, readonly distance: number) { }
}

class Result {
  constructor(readonly stage: Stage, readonly position: number) { }
}

const stages = [
  new Stage("SS1", 0), new Stage("SS2", 1), new Stage("SS3", 2), new Stage("SS4", 3)]

const data = [
  {
    name: "Kalle", results: [
      new Result(stages[0], 1),
      new Result(stages[1], 1),
      new Result(stages[2], 2)
    ]
  },
  {
    name: "Ott", results: [
      new Result(stages[0], 2),
      new Result(stages[1], 2),
      new Result(stages[2], 1)
    ]
  },
  {
    name: "Taka", results: [
      new Result(stages[0], 3),
      new Result(stages[1], 3),
      new Result(stages[2], 3)
    ]
  }
]

const xScale = scaleLinear()
  .domain(extent(stages, s => s.distance) as [number, number])
  .range([margin.left, width - margin.right])
const yScale = scaleLinear()
  .domain([1, data.length])
  .range([margin.top, height - margin.bottom])
const colorScale = scaleOrdinal<string>().domain(data.map(d => d.name)).range(schemeCategory10)

function xAxis(selection: Selection<SVGGElement, any, HTMLElement, any>): void {
  selection.style("font", "10px sans-serif")
    .selectAll("g").data(stages).join("g")
    .attr("transform", d => `translate(${xScale(d.distance)},0)`)
    // top dash
    .call(g => g.append("line")
      .attr("y1", margin.top - 6)
      .attr("y2", margin.top)
      .attr("stroke", "currentColor"))
    // bottom dash
    .call(g => g.append("line")
      .attr("y1", height - margin.bottom + 6)
      .attr("y2", height - margin.bottom)
      .attr("stroke", "currentColor"))
    // top stage name
    .call(g => g.append("text")
      .attr("transform", `translate(0,${margin.top}) rotate(-90)`)
      .attr("x", 12)
      .attr("dy", "0.35em")
      .text(d => d.name))
    // bottom stage name
    .call(g => g.append("text")
      .attr("text-anchor", "end")
      .attr("transform", `translate(0,${height - margin.top}) rotate(-90)`)
      .attr("x", -12)
      .attr("dy", "0.35em")
      .text(d => d.name))
}

function yAxis(selection: Selection<SVGGElement, any, HTMLElement, any>): void {
  selection.style("font", "10px sans-serif")
    .selectAll("g").data(data).join("g")
    .attr("transform", d => `translate(0, ${yScale(d.results[0].position)})`)
    .call(g => g.append("text")
      .attr("dy", "0.4em")
      .text(d => d.name))
}

function resultLines(selection: Selection<SVGGElement, any, HTMLElement, any>): void {
  function mkLine() {
    return line<Result>().x(d => xScale(d.stage.distance)).y(d => yScale(d.position))
  }

  const result = selection.attr("stroke-width", 1.5)
    .selectAll("g")
    .data(data)
    .join("g")

  result.append("path")
    .attr("fill", "none")
    .attr("stroke", d => colorScale(d.name))
    .attr("d", d => mkLine()(d.results));

  result.append("g")
    .attr("stroke", "white")
    .attr("fill", d => colorScale(d.name))
    .selectAll("circle")
    .data(d => d.results)
    .join("circle")
    .attr("transform", d => `translate(${xScale(d.stage.distance)},${yScale(d.position)})`)
    .attr("r", 7.5)
}

export function buildChart() {
  const chart = select("#chart").append("svg").attr("viewBox", [0, 0, width, height])

  chart.append("g").call(xAxis)
  chart.append("g").call(yAxis)
  chart.append("g").call(resultLines)
}
