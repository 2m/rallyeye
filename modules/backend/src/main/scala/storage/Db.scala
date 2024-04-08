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

import cats.*
import cats.effect.*
import cats.implicits.*
import com.ovoenergy.natchez.extras.doobie.TracedTransactor
import doobie.*
import doobie.implicits.*
import doobie.implicits.javatimedrivernative.*
import doobie.util.fragments.whereAndOpt
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.github.iltotore.iron.doobie.given
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.RallyKind

extension (kind: RallyKind)
  def cond: Fragment =
    fr"kind = $kind"

object Db:
  case class Config(
      url: String,
      migrationsTable: String,
      migrationsLocations: List[String]
  )

  val file = sys.env.get("RALLYEYE_DB_PATH").map(_ + "/").getOrElse("") + "rallyeye.db"

  val config = Config(
    url = s"jdbc:sqlite:$file",
    migrationsTable = "flyway",
    migrationsLocations = List("classpath:db")
  )

  def xa[F[_]: Async] =
    val transactor = Transactor.fromDriverManager[F](
      driver = "org.sqlite.JDBC",
      url = config.url,
      logHandler = None
    )
    Transactor.before.modify(transactor, sql"PRAGMA foreign_keys = 1".update.run *> _)

  def tracedTransactor[F[_]: Async: Tracer] =
    given natchez.Trace[F] = rallyeye.NatchezOtel4s.fromOtel4s[F](summon[Tracer[F]])
    TracedTransactor.trace(com.ovoenergy.natchez.extras.core.Config.UseExistingNames, xa, LogHandler.noop[F])

  given [A](using Encoder[A]): Put[List[A]] =
    Put[String].tcontramap(io.bullet.borer.Json.encode(_).toUtf8String)

  given [A](using Decoder[A]): Get[List[A]] =
    Get[String].tmap(s => io.bullet.borer.Json.decode(s.getBytes("UTF8")).to[List[A]].value)

  def insertRally[F[_]: Async: Tracer](rally: Rally) =
    sql"""|insert or replace into rally (
          |  kind,
          |  external_id,
          |  name,
          |  retrieved_at,
          |  championship,
          |  start,
          |  end,
          |  distance_meters,
          |  started,
          |  finished
          |) values ($rally)""".stripMargin.update.run.attemptSql
      .transact(tracedTransactor[F])
      .map(_.left.map(ex => ex: Throwable))

  def selectRally[F[_]: Async: Tracer](externalId: String)(using kind: RallyKind) =
    sql"""|select
          |  kind,
          |  external_id,
          |  name,
          |  retrieved_at,
          |  championship,
          |  start,
          |  end,
          |  distance_meters,
          |  started,
          |  finished
          |from rally where kind = $kind and external_id = $externalId""".stripMargin
      .query[Rally]
      .option
      .attemptSql
      .transact(tracedTransactor[F])

  def selectRallies[F[_]: Async: Tracer]() =
    sql"""|select
          |  kind,
          |  external_id
          |from rally""".stripMargin
      .query[(RallyKind, String)]
      .to[List]
      .attemptSql
      .transact(tracedTransactor[F])

  def findRallies[F[_]: Async: Tracer](championship: String, year: Option[Int])(using kind: RallyKind) =
    val query = fr"select * from rally"
    val championshipCond = fr"exists (select 1 from json_each(championship) where value = $championship)"
    val yearCond = year.map(y => fr"strftime('%Y', start) = ${y.toString}")
    (query ++ whereAndOpt(Some(kind.cond), Some(championshipCond), yearCond))
      .query[Rally]
      .to[List]
      .attemptSql
      .transact(tracedTransactor[F])
      .map(_.left.map(ex => ex: Throwable))

  def insertManyResults[F[_]: Async: Tracer](results: List[Result]) =
    val sql = """|insert or replace into results (
                 |  rally_kind,
                 |  rally_external_id,
                 |  stage_number,
                 |  stage_name,
                 |  driver_country,
                 |  driver_primary_name,
                 |  driver_secondary_name,
                 |  codriver_country,
                 |  codriver_primary_name,
                 |  codriver_secondary_name,
                 |  `group`,
                 |  car,
                 |  stage_time_ms,
                 |  penalty_inside_stage_ms,
                 |  penalty_outside_stage_ms,
                 |  super_rally,
                 |  finished,
                 |  comment,
                 |  nominal
                 |) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".stripMargin
    Update[Result](sql)
      .updateMany(results)
      .attemptSql
      .transact(tracedTransactor[F])
      .map(_.left.map(ex => ex: Throwable))

  def selectResults[F[_]: Async: Tracer](externalId: String)(using kind: RallyKind) =
    sql"""|select
          |  rally_kind,
          |  rally_external_id,
          |  stage_number,
          |  stage_name,
          |  driver_country,
          |  driver_primary_name,
          |  driver_secondary_name,
          |  codriver_country,
          |  codriver_primary_name,
          |  codriver_secondary_name,
          |  `group`,
          |  car,
          |  stage_time_ms,
          |  penalty_inside_stage_ms,
          |  penalty_outside_stage_ms,
          |  super_rally,
          |  finished,
          |  comment,
          |  nominal
          |from results where rally_kind = $kind and rally_external_id = $externalId""".stripMargin
      .query[Result]
      .to[List]
      .attemptSql
      .transact(tracedTransactor[F])
      .map(_.left.map(ex => ex: Throwable))

  def deleteResultsAndRally[F[_]: Async: Tracer](externalId: String)(using kind: RallyKind) =
    val deleteResults =
      sql"""|delete from results
            |where
            |  rally_kind = $kind and
            |  rally_external_id = $externalId""".stripMargin.update.run

    val deleteRally =
      sql"""|delete from rally
            |where
            |  kind = $kind and
            |  external_id = $externalId""".stripMargin.update.run

    (deleteResults, deleteRally)
      .mapN(_ + _)
      .attemptSql
      .transact(tracedTransactor[F])
      .map(_.left.map(ex => ex: Throwable))
