dev:
  tmux new-session -s rallyeye "tmux source-file './.tmux.conf'"

dev-js:
  cd modules/frontend; npm run dev

dev-scala-js:
  sbt --client ~frontend/fastLinkJS

dev-scala:
  sbt --client ~backend/reStart http-server

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

migrate:
  sbt --client backend/run migrate-db

rm-db:
  rm modules/backend/rallyeye.db

test:
  sbt --client test

test-integration:
  sbt --client Integration/test
