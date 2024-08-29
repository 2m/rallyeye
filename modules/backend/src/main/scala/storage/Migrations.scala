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

package rallyeye
package storage

import java.time.LocalDate

import scala.io.Source

import cats.effect.kernel.Async
import cats.syntax.all.*
import fly4s.Fly4s
import fly4s.data.Fly4sConfig
import fly4s.data.Locations
import fly4s.data.ValidatePattern
import fly4s.implicits.*
import io.github.iltotore.iron.*
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.RallyKind

def migrations[F[_]: Async: Tracer] = Fly4s
  .make[F](
    url = Db.config.url,
    config = Fly4sConfig(
      table = Db.config.migrationsTable,
      locations = Locations(Db.config.migrationsLocations),
      ignoreMigrationPatterns = List(
        ValidatePattern.ignorePendingMigrations
      ),
      resourceProvider = Option(
        System.getProperty("org.graalvm.nativeimage.imagecode")
      ).map(_ => new GraalVMResourceProvider(Locations(Db.config.migrationsLocations)))
    )
  )
  .evalMap(_.validateAndMigrate.result.traced("fly4s-migration"))

def insertPressAutoResults[F[_]: Async: Tracer](rallyId: String, rallyInfo: RallyInfo, filename: String) =
  given RallyKind = RallyKind.PressAuto
  (for
    csv <- Source.fromResource(filename)(scala.io.Codec.UTF8).mkString.pure[F]
    results <- PressAuto.parseResults(csv).pure[F]
    _ <- Repo.saveRallyInfo(rallyId, rallyInfo)
    _ <- Repo.saveRallyResults(rallyId, results)
  yield ()).tracedR("insert-press-auto")

def allMigrations[F[_]: Async: Tracer] =
  for
    _ <- rallyeye.storage.migrations
    _ <- rallyeye.storage
      .insertPressAutoResults(
        "2023",
        RallyInfo(
          "Press Auto 2023",
          List("Press Auto"),
          LocalDate.of(2023, 6, 16),
          LocalDate.of(2023, 6, 17),
          60000,
          68,
          63
        ),
        "pressauto2023.csv"
      )
    _ <- rallyeye.storage
      .insertPressAutoResults(
        "2024",
        RallyInfo(
          "Press Auto 2024",
          List("Press Auto"),
          LocalDate.of(2024, 5, 31),
          LocalDate.of(2024, 6, 1),
          36640,
          77,
          72
        ),
        "pressauto2024.csv"
      )
  yield ()
