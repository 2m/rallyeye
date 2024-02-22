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

import cats.effect.IO
import fly4s.Fly4s
import fly4s.data.Fly4sConfig
import fly4s.data.Locations
import fly4s.data.ValidatePattern
import fly4s.implicits.*
import io.github.iltotore.iron.*

val migrations = Fly4s
  .make[IO](
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
  .evalMap(_.validateAndMigrate.result)

def loadPressAutoResults(rallyId: String, rallyInfo: RallyInfo, filename: String) =
  for
    csv <- IO.pure(Source.fromResource(filename)(scala.io.Codec.UTF8).mkString)
    results <- IO.pure(PressAuto.parseResults(csv))
    _ <- Repo.PressAuto.saveRallyInfo(rallyId, rallyInfo)
    _ <- Repo.PressAuto.saveRallyResults(rallyId, results)
  yield ()

val allMigrations = rallyeye.storage.migrations
  .use(_ => IO(())) <* rallyeye.storage.loadPressAutoResults(
  "2023",
  RallyInfo(
    "Press Auto 2023",
    Some("Press Auto"),
    LocalDate.of(2023, 6, 16),
    LocalDate.of(2023, 6, 17),
    60000.refine,
    68.refine,
    63.refine
  ),
  "pressauto2023.csv"
)
