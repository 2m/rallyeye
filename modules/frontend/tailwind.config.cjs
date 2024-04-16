/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./index.html",
    "./target/scala-*/frontend-*/*.js",
  ],
  theme: {
    fontFamily: {
      sans: ['Twemoji Country Flags', 'Arial', 'sans-serif'],
    },
    extend: {},
  },
  plugins: [],
}
