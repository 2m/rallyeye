dev:
  tmux new-session -s rallyeye "tmux source-file './.tmux.conf'"

dev-js:
  cd modules/frontend; npm run dev

dev-scala-js:
  sbt --client ~frontend/fastLinkJS

dev-scala:
  sbt --client ~backend/reStart

build-scala:
  sbt --client publicProd

build:
  cd modules/frontend; npm run build

install:
  cd modules/frontend; npm install

serve:
  cd dist; webfsd -p 8001 -F -f index.html

build-backend:
  sbt --client backend/jibImageBuild

deploy-backend:
  cd modules/backend; flyctl deploy
