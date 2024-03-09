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
import rallyeye.shared.RallyKind
import rallyeye.shared.RallySummary

object Repo:
  def saveRallyInfo(rallyKind: RallyKind)(rallyId: String, info: RallyInfo) =
    info
      .into[Rally]
      .transform(
        Field.const(_.kind, rallyKind),
        Field.const(_.externalId, rallyId.toString),
        Field.const(_.retrievedAt, Instant.now)
      )
      .pipe(Db.insertRally)

  def getRally(rallyKind: RallyKind)(rallyId: String) =
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
            Field.const(_.codriverSecondaryName, None)
          )
      )
      .pipe(Db.insertManyResults)

  def getRsfRallyResults(rallyKind: RallyKind)(rallyId: String) =
    (for results <- EitherT(Db.selectResults(rallyKind, rallyId))
    yield results.map(
      _.into[Entry].transform(
        Field.renamed(_.country, _.driverCountry),
        Field.renamed(_.userName, _.driverPrimaryName),
        Field.const(_.realName, ""), // FIXME: realName should be Option
        Field.const(_.split1Time, None),
        Field.const(_.split2Time, None),
        Field.const(_.finishRealtime, None),
        Field.computed(_.comment, r => r.comment.getOrElse("")) // FIXME: comment should be Option
      )
    )).value

  def findRallies(championship: String, year: Option[Int])(using kind: RallyKind) =
    for results <- EitherT(Db.findRallies(championship, year))
    yield results.map(
      _.into[RallySummary].transform()
    )

  def deleteResultsAndRally(rallyKind: RallyKind)(rallyId: String) =
    Db.deleteResultsAndRally(rallyKind, rallyId)

  object Rsf:
    val saveRallyInfo = Repo.saveRallyInfo(RallyKind.Rsf)
    val getRally = Repo.getRally(RallyKind.Rsf)
    val saveRallyResults = Repo.saveRsfRallyResults(RallyKind.Rsf)
    val getRallyResults = Repo.getRsfRallyResults(RallyKind.Rsf)
    val deleteResultsAndRally = Repo.deleteResultsAndRally(RallyKind.Rsf)

  object PressAuto:
    val saveRallyInfo = Repo.saveRallyInfo(RallyKind.PressAuto)
    val getRally = Repo.getRally(RallyKind.PressAuto)
    val saveRallyResults = Repo.saveRsfRallyResults(RallyKind.PressAuto)
    val getRallyResults = Repo.getRsfRallyResults(RallyKind.PressAuto)

  object Ewrc:
    val saveRallyInfo = Repo.saveRallyInfo(RallyKind.Ewrc)
    val getRally = Repo.getRally(RallyKind.Ewrc)
    val saveRallyResults = Repo.saveRsfRallyResults(RallyKind.Ewrc)
    val getRallyResults = Repo.getRsfRallyResults(RallyKind.Ewrc)
    val deleteResultsAndRally = Repo.deleteResultsAndRally(RallyKind.Ewrc)
