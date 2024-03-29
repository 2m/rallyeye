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
import cats.effect.kernel.Async
import io.github.arainko.ducktape.*
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.RallyKind
import rallyeye.shared.RallySummary

object Repo:
  def saveRallyInfo[F[_]: Async: Tracer](rallyId: String, info: RallyInfo)(using kind: RallyKind) =
    info
      .into[Rally]
      .transform(
        Field.const(_.kind, kind),
        Field.const(_.externalId, rallyId.toString),
        Field.const(_.retrievedAt, Instant.now)
      )
      .pipe(Db.insertRally)

  def getRally[F[_]: Async: Tracer](rallyId: String)(using kind: RallyKind) =
    Db.selectRally(rallyId.toString)

  def saveRallyResults[F[_]: Async: Tracer](rallyId: String, results: List[Entry])(using kind: RallyKind) =
    results
      .map(
        _.into[Result]
          .transform(
            Field.const(_.rallyKind, kind),
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

  def getRallyResults[F[_]: Async: Tracer](rallyId: String)(using kind: RallyKind) =
    (for results <- EitherT(Db.selectResults(rallyId))
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

  def findRallies[F[_]: Async: Tracer](championship: String, year: Option[Int])(using kind: RallyKind) =
    for
      results <- EitherT(Db.findRallies[F](championship, year))
      transformed <- EitherT.rightT(results.map(_.into[RallySummary].transform()))
    yield transformed

  def freshRallies[F[_]: Async: Tracer]() =
    for
      results <- EitherT(Db.freshRallies[F]())
      transformed <- EitherT.rightT(results.map(_.into[RallySummary].transform()))
    yield transformed

  def deleteResultsAndRally[F[_]: Async: Tracer](rallyId: String)(using kind: RallyKind) =
    Db.deleteResultsAndRally(rallyId)
