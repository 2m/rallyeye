/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./target/scala-*/frontend-*/*.js",
    "./node_modules/flowbite/**/*.js"
  ],
  theme: {
    fontFamily: {
      sans: ['Twemoji Country Flags', 'Graphik', 'sans-serif'],
    },
    extend: {},
  },
  plugins: [require('flowbite/plugin')],
}