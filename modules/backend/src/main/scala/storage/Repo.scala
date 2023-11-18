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

import java.time.Instant

import scala.util.chaining.*

import cats.data.EitherT
import cats.effect.IO
import io.github.arainko.ducktape.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

object Repo:
  def saveRallyName(rallyKind: RallyKind)(rallyId: String, name: String) =
    Db.insertRally(Rally(rallyKind, rallyId.toString, name, Instant.now))

  def getRally(rallyKind: RallyKind)(rallyId: Int) =
    Db.selectRally(rallyKind, rallyId.toString)

  def saveRsfRallyResults(rallyKind: RallyKind)(rallyId: String, results: List[Entry]) =
    results
      .map(
        _.into[Result]
          .transform(
            Field.const(_.rallyKind, rallyKind),
            Field.const(_.externalId, rallyId.toString),
            Field.renamed(_.driverCountry, _.country),
            Field.renamed(_.driverPrimaryName, _.userName),
            Field.const(_.driverSecondaryName, None),
            Field.const(_.codriverCountry, None),
            Field.const(_.codriverPrimaryName, None),
            Field.const(_.codriverSecondaryName, None),
            Field.computed(_.stageTimeMs, r => (r.stageTime * 1000).toInt.refine[GreaterEqual[0]])
          )
      )
      .pipe(Db.insertManyResults)

  def getRsfRallyResults(rallyKind: RallyKind)(rallyId: Int) =
    (for {
      results <- EitherT(Db.selectResults(rallyKind, rallyId.toString))
    } yield results.map(
      _.into[Entry].transform(
        Field.renamed(_.country, _.driverCountry),
        Field.renamed(_.userName, _.driverPrimaryName),
        Field.const(_.realName, ""), // FIXME: realName should be Option
        Field.const(_.split1Time, None),
        Field.const(_.split2Time, None),
        Field.computed(_.stageTime, r => BigDecimal(r.stageTimeMs) / 1000),
        Field.const(_.finishRealtime, None),
        Field.const(_.penalty, None),
        Field.const(_.servicePenalty, None),
        Field.computed(_.comment, r => r.comment.getOrElse("")) // FIXME: comment should be Option
      )
    )).value

  object Rsf:
    def saveRallyName(rallyId: Int, name: String) = Repo.saveRallyName(RallyKind.Rsf)(rallyId.toString, name)
    val getRally = Repo.getRally(RallyKind.Rsf)
    def saveRallyResults(rallyId: Int, results: List[Entry]) =
      Repo.saveRsfRallyResults(RallyKind.Rsf)(rallyId.toString, results)
    val getRallyResults = Repo.getRsfRallyResults(RallyKind.Rsf)

  object PressAuto:
    val saveRallyName = Repo.saveRallyName(RallyKind.PressAuto)
    val getRally = Repo.getRally(RallyKind.PressAuto)
    val saveRallyResults = Repo.saveRsfRallyResults(RallyKind.PressAuto)
    val getRallyResults = Repo.getRsfRallyResults(RallyKind.PressAuto)
