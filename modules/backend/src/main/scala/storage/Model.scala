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
import java.time.LocalDate

import doobie.implicits.javatimedrivernative.*
import doobie.util.{Get, Put}
import doobie.util.Read
import doobie.util.Write
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.doobie.given
import rallyeye.shared.RallyKind

given Write[RallyKind] = Write[Int].contramap(_.ordinal)
given Read[RallyKind] = Read[Int].map(RallyKind.fromOrdinal)

given Write[Instant] = Write[Long].contramap(_.getEpochSecond)
given Read[Instant] = Read[Long].map(Instant.ofEpochSecond)

case class Rally(
    kind: RallyKind,
    externalId: String,
    name: String,
    retrievedAt: Instant,
    championship: List[String],
    start: LocalDate,
    end: LocalDate,
    distanceMeters: Int :| Greater[0],
    started: Int :| GreaterEqual[0],
    finished: Int :| GreaterEqual[0]
) derives Read,
      Write

case class Result(
    rallyKind: RallyKind,
    externalId: String,
    stageNumber: Int :| Greater[0],
    stageName: String,
    driverCountry: String,
    driverPrimaryName: String,
    driverSecondaryName: Option[String],
    codriverCountry: Option[String],
    codriverPrimaryName: Option[String],
    codriverSecondaryName: Option[String],
    group: List[String],
    car: String,
    stageTimeMs: Int :| GreaterEqual[0],
    penaltyInsideStageMs: Int :| GreaterEqual[0],
    penaltyOutsideStageMs: Int :| GreaterEqual[0],
    superRally: Boolean,
    finished: Boolean,
    comment: Option[String],
    nominal: Boolean
) derives Read,
      Write

given [A](using Encoder[A]): Put[List[A]] =
  Put[String].tcontramap(io.bullet.borer.Json.encode(_).toUtf8String)

given [A](using Decoder[A]): Get[List[A]] =
  Get[String].tmap(s => io.bullet.borer.Json.decode(s.getBytes("UTF8")).to[List[A]].value)
