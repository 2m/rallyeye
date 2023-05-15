dev-scala:
  sbt --client ~frontend/fastLinkJS

dev-js:
  cd modules/frontend; npm run dev

build:
  cd modules/frontend; npm run build

serve:
  cd dist; webfsd -p 8001 -F -f index.html
