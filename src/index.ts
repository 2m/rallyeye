import { selectAll } from "d3-selection"

const data = [
  {}
]

export function bg_color() {
  const myDiv = selectAll("#myDiv")
  myDiv.style("background-color", "red")
}
