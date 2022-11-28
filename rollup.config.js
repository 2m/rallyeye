import typescript from "@rollup/plugin-typescript"
import { nodeResolve } from "@rollup/plugin-node-resolve"

export default {
  input: "src/index.ts",
  output: {
    dir: "dist",
    format: "iife",
    name: "graph_tools",
  },
  sourceMap: true,
  plugins: [
    typescript({ tsconfig: "./tsconfig.json" }),
    nodeResolve({ modulesOnly: true }),
  ],
}
