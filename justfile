set dotenv-load

dev:
  process-compose -p 8088 up

dev-js:
  cd modules/frontend; npm run dev

dev-scala-js:
  sbt --client ~frontend/fastLinkJS

dev-scala:
  sbt --client ~backend/reStart http-server

build-scala-js:
  sbt --client publicProd

build-js:
  cd modules/frontend; npm run build

install:
  cd modules/frontend; npm install

serve:
  cd modules/frontend/dist; caddy file-server --listen :8001

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

run-native-image:
  modules/backend/target/native-image/backend -Dcats.effect.trackFiberContext=true http-server

rm-db:
  rm modules/backend/rallyeye.db

test:
  sbt --client test

test-integration:
  sbt --client Integration/test

docker-login:
  cd modules/backend; fly auth token | docker login registry.fly.io --username=x --password-stdin

clone region:
  cd modules/backend; fly machine clone --select --region {{region}}

ssh:
  cd modules/backend; fly ssh console --select

litefs-export:
  cd modules/backend; fly litefs-cloud export --cluster rallyeye --database rallyeye.db --output ./rallyeye.db.$(date "+%Y-%m-%d")

telemetry:
  cd telemetry; docker-compose up

delete-rally kind rally:
  http --auth admin:$ADMIN_PASS POST 'https://rallyeye-data.fly.dev/admin/delete/{{kind}}/{{rally}}'
