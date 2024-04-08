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
  sbt --client backend/dockerBuildAndPush

deploy-backend:
  cd modules/backend; flyctl deploy

migrate:
  sbt --client backend/run migrate-db

smoke:
  sbt --client backend/run smoke-run

smoke-with-agent:
  rm -f modules/backend/rallyeye.db
  sbt --client backend/nativeImageRunAgent \" smoke-run\"

native-image:
  sbt --client backend/nativeImage

rm-db:
  rm modules/backend/rallyeye.db

test:
  sbt --client test

test-integration:
  sbt --client Integration/test

docker-login:
  cd modules/backend; flyctl auth token | docker login registry.fly.io --username=x --password-stdin

clone region:
  cd modules/backend; fly machine clone --select --region {{region}}

ssh:
  fly ssh console --select

litefs-export:
  cd modules/backend; flyctl litefs-cloud export --cluster rallyeye --database rallyeye.db --output ./rallyeye.db.$(date "+%Y-%m-%d")

telemetry:
  cd telemetry; docker-compose up
