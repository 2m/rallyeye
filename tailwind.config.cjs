/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./target/scala-3.2.1/rallyeye-scala-fastopt/*.js",
    "./target/scala-3.2.1/rallyeye-scala-opt/*.js"
  ],
  theme: {
    fontFamily: {
      sans: ['Graphik', 'sans-serif'],
    },
    extend: {},
  },
  plugins: [],
}
