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

import scala.io.Source

import cats.effect.IO
import fly4s.core.Fly4s
import fly4s.core.data.Fly4sConfig
import fly4s.core.data.Locations
import fly4s.core.data.ValidatePattern
import fly4s.implicits.*

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

def loadPressAutoResults(rallyId: String, name: String, filename: String) =
  for
    csv <- IO.pure(Source.fromResource(filename)(scala.io.Codec.UTF8).mkString)
    results <- IO.pure(PressAuto.parseResults(csv))
    _ <- Repo.PressAuto.saveRallyName(rallyId, name)
    _ <- Repo.PressAuto.saveRallyResults(rallyId, results)
  yield ()

val allMigrations = rallyeye.storage.migrations
  .use(_ => IO(())) <* rallyeye.storage.loadPressAutoResults("2023", "Press Auto 2023", "pressauto2023.csv")
