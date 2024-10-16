/*
 * Copyright 2022 github.com/2m/rallyeye/contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import rallyeye.Telemetry

object Main
    extends CommandIOApp(
      name = "rallyeye",
      header = "RallyEye command line"
    ):

  case class HttpServer()
  case class MigrateDb()
  case class SmokeRun()
  case class LoadPressAuto()

  val httpServer: Opts[HttpServer] =
    Opts.subcommand("http-server", "Runs RallyEye HTTP server.") {
      Opts.unit.map(_ => HttpServer())
    }

  val migrateDb: Opts[MigrateDb] =
    Opts.subcommand("migrate-db", "Runs RallyEye DB migrations.") {
      Opts.unit.map(_ => MigrateDb())
    }

  val smokeRun: Opts[SmokeRun] =
    Opts.subcommand("smoke-run", "Exercice all functionality of the app.") {
      Opts.unit.map(_ => SmokeRun())
    }

  val parsePressAuto: Opts[LoadPressAuto] =
    Opts.subcommand("load-press-auto", "Load Press Auto data from raceadmin.eu endpoints to csv file") {
      Opts.unit.map(_ => LoadPressAuto())
    }

  override def main: Opts[IO[ExitCode]] =
    (httpServer orElse migrateDb orElse smokeRun orElse parsePressAuto)
      .map {
        case HttpServer() => Telemetry.instrument(rallyeye.httpServer[IO]).use(_ => IO.never)
        case MigrateDb() => Telemetry.instrument(rallyeye.storage.allMigrations[IO]).use(_ => ExitCode.Success.pure[IO])
        case SmokeRun()  => Telemetry.instrument(rallyeye.smokeRun[IO]).use(_ => ExitCode.Success.pure[IO])
        case LoadPressAuto() =>
          Telemetry.instrument(rallyeye.loader.loadPressAuto[IO]).use(_ => ExitCode.Success.pure[IO])
      }
