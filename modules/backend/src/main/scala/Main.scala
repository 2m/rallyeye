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
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp

object Main
    extends CommandIOApp(
      name = "rallyeye",
      header = "RallyEye command line"
    ):

  case class HttpServer()
  case class MigrateDb()
  case class SmokeRun()

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

  override def main: Opts[IO[ExitCode]] =
    (httpServer orElse migrateDb orElse smokeRun).map {
      case HttpServer() => rallyeye.httpServer.flatMap(_.use(_ => IO.never))
      case MigrateDb()  => rallyeye.storage.allMigrations.map(_ => ExitCode.Success)
      case SmokeRun()   => rallyeye.smokeRun.map(_ => ExitCode.Success)
    }
