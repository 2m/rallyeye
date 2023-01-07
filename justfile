dev-scala:
  sbtn ~fastLinkJS

dev-js:
  npm run dev

build:
  npm run build

serve:
  cd dist; webfsd -p 8001 -F -f index.html
