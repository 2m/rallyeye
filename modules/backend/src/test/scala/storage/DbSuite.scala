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

import java.nio.file.Files
import java.nio.file.Paths
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate

import scala.collection.immutable.ArraySeq

import cats.effect.IO
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import doobie.implicits.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.scalacheck.numeric.given
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.effect.PropF
import org.scalacheck.ops.*
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.RallyKind

class DbSuite extends CatsEffectSuite with ScalaCheckEffectSuite with DiffxAssertions with IronDiffxSupport:
  import Tracer.Implicits.noop

  given Diff[Instant] = Diff[Long].contramap(_.getEpochSecond)
  given Diff[RallyKind] = Diff.derived[RallyKind]
  given Diff[Rally] = Diff.derived[Rally]
  given Diff[Result] = Diff.derived[Result]
  given Diff[Throwable] = Diff[String].contramap(_.getMessage)
  given Diff[SQLException] = Diff[String].contramap(_.getMessage)

  case class RallyWithResults(rally: Rally, results: List[Result])

  given Arbitrary[RallyKind] = Arbitrary:
    Gen.oneOf(ArraySeq.unsafeWrapArray(RallyKind.values))
  given Arbitrary[Rally] = Arbitrary:
    Gen.resultOf(Rally.apply)
  given Arbitrary[Result] = Arbitrary:
    Gen.resultOf(Result.apply)
  given Arbitrary[RallyWithResults] = Arbitrary:
    for
      rally <- arbitrary[Rally]
      stageCount <- Gen.oneOf(1 to 10)
      stageNumbers <- Gen.setOfN(stageCount, arbitrary[Int :| Greater[0]])
      driverCount <- Gen.oneOf(1 to 10)
      driverNames <- Gen.setOfN(driverCount, arbitrary[String])
    yield
      val results =
        for
          stageNumber <- stageNumbers
          driverName <- driverNames
        yield arbitrary[Result].sample.get.copy(
          rallyKind = rally.kind,
          externalId = rally.externalId,
          stageNumber = stageNumber,
          driverPrimaryName = driverName
        )
      RallyWithResults(rally, results.toList)

  val db = ResourceFunFixture:
    for
      _ <- IO.blocking(Files.deleteIfExists(Paths.get(Db.file))).toResource
      _ <- migrations[IO]
    yield ()

  db.test("should insert and select rally"): _ =>
    PropF.forAllF: (rally: Rally) =>
      for
        inserted <- Db.insertRally[IO](rally)
        _ = assertEqual(inserted, Right(1))

        given RallyKind = rally.kind
        selected <- Db.selectRally[IO](rally.externalId)
        _ = assertEqual(selected, Right(Some(rally)))
      yield ()

  db.test("should insert and select results"): _ =>
    PropF.forAllF: (rs: RallyWithResults) =>
      for
        _ <- sql"delete from results".update.run.attemptSql.transact(Db.tracedTransactor[IO])
        RallyWithResults(rally, results) = rs

        _ <- Db.insertRally[IO](rally)
        inserted <- Db.insertManyResults[IO](results)
        _ = assertEqual(inserted, Right(results.size))

        given RallyKind = rs.rally.kind
        selected <- Db.selectResults[IO](rally.externalId)
        _ = assertEqual(selected, Right(results))
      yield ()

  db.test("should insert or update rally"): _ =>
    for
      rally <- arbitrary[Rally]
      inserted <- Db.insertRally[IO](rally)
      _ = assertEqual(inserted, Right(1))

      rally2 = rally.copy(name = rally.name + " changed")
      inserted2 <- Db.insertRally[IO](rally2)
      _ = assertEqual(inserted2, Right(1))

      given RallyKind = rally.kind
      selected <- Db.selectRally[IO](rally.externalId)
      _ = assertEqual(selected, Right(Some(rally2)))
    yield ()

  db.test("should insert or update results"): _ =>
    for
      RallyWithResults(rally, results) <- arbitrary[RallyWithResults]
      result = results.head

      _ <- Db.insertRally[IO](rally)
      inserted <- Db.insertManyResults[IO](List(result))
      _ = assertEqual(inserted, Right(1))

      result2 = result.copy(stageName = result.stageName + " changed")
      inserted2 <- Db.insertManyResults[IO](List(result2))
      _ = assertEqual(inserted2, Right(1))

      given RallyKind = rally.kind
      selected <- Db.selectResults[IO](rally.externalId)
      _ = assertEqual(selected, Right(List(result2)))
    yield ()

  db.test("should select all rallies"): _ =>
    for
      rally1 <- arbitrary[Rally]
      rally2 <- arbitrary[Rally]

      _ <- Db.insertRally[IO](rally1)
      _ <- Db.insertRally[IO](rally2)

      selected <- Db.selectRallies[IO]()
      _ = assertEqual(
        selected.map(_.toSet),
        Right(Set((rally1.kind, rally1.externalId), (rally2.kind, rally2.externalId)))
      )
    yield ()

  db.test("should delete rally and results"): _ =>
    for
      RallyWithResults(rally, results) <- arbitrary[RallyWithResults]
      _ <- Db.insertRally[IO](rally)
      _ <- Db.insertManyResults[IO](results)

      given RallyKind = rally.kind
      selectedRally <- Db.selectRally[IO](rally.externalId)
      _ = assertEqual(selectedRally, Right(Some(rally)))

      selectedResults <- Db.selectResults[IO](rally.externalId)
      _ = assertEquals(selectedResults, Right(results))

      _ <- Db.deleteResultsAndRally[IO](rally.externalId)

      deletedRally <- Db.selectRally[IO](rally.externalId)
      _ = assertEqual(deletedRally, Right(None))

      deletedResults <- Db.selectResults[IO](rally.externalId)
      _ = assertEquals(deletedResults, Right(Nil))
    yield ()

  db.test("should find rallies by championship"): _ =>
    for
      rally1 <- arbitrary[Rally].map(_.copy(championship = List("champ1", "champ2")))
      rally2 <- arbitrary[Rally].map(_.copy(championship = List("champ2")))

      _ <- Db.insertRally[IO](rally1)
      _ <- Db.insertRally[IO](rally2)

      given RallyKind = rally1.kind
      selected <- Db.findRallies[IO]("champ1", None)
      _ = assertEqual(selected, Right(List(rally1)))
    yield ()

  db.test("should not find rallies of different kind"): _ =>
    for
      rally1 <- arbitrary[Rally].map(_.copy(championship = List("champ1")))
      _ <- Db.insertRally[IO](rally1)

      given RallyKind = RallyKind.values.find(_ != rally1.kind).get
      selected <- Db.findRallies[IO]("champ1", None)
      _ = assertEqual(selected, Right(List.empty))
    yield ()

  db.test("should find rallies by championship and year"): _ =>
    for
      rally1 <- arbitrary[Rally].map(_.copy(championship = List("champ1"), start = LocalDate.parse("2022-01-01")))
      rally2 <- arbitrary[Rally].map(_.copy(championship = List("champ1"), start = LocalDate.parse("2023-01-01")))

      _ <- Db.insertRally[IO](rally1)
      _ <- Db.insertRally[IO](rally2)

      given RallyKind = rally1.kind
      selected <- Db.findRallies[IO]("champ1", Some(2022))
      _ = assertEqual(selected, Right(List(rally1)))
    yield ()
