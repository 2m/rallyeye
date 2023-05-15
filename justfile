dev-scala:
  sbt --client ~frontend/fastLinkJS

dev-js:
  cd modules/frontend; npm run dev

build-scala:
  sbt --client publicProd

build:
  cd modules/frontend; npm run build

install:
  cd modules/frontend; npm install

serve:
  cd dist; webfsd -p 8001 -F -f index.html
