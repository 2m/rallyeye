import typescript from "@rollup/plugin-typescript"
import serve from 'rollup-plugin-serve'
import copy from 'rollup-plugin-copy'
import { nodeResolve } from "@rollup/plugin-node-resolve"

export default {
  input: "src/index.ts",
  output: {
    dir: "dist",
    format: "iife",
    name: "rallyeye",
  },
  sourceMap: true,
  plugins: [
    typescript({ tsconfig: "./tsconfig.json" }),
    nodeResolve({ modulesOnly: true }),
    serve("dist"),
    copy({
      targets: [
        { src: 'public/index.html', dest: 'dist/' },
      ]
    })
  ],
}
