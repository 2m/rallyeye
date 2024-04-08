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

package rallyeye.shared

import java.time.Instant

import Codecs.given
import TapirJsonBorer.*
import sttp.tapir.*
import sttp.tapir.Codec as TapirCodec
import sttp.tapir.generic.auto.*
import sttp.tapir.model.UsernamePassword

type Endpoint[Req, Resp] = sttp.tapir.Endpoint[Unit, Req, ErrorInfo, Resp, Any]

sealed trait ErrorInfo
case class GenericError(message: String) extends ErrorInfo
case class RallyNotStored() extends ErrorInfo
case class RallyInProgress() extends ErrorInfo
case class RefreshNotSupported() extends ErrorInfo

given TapirCodec.PlainCodec[RallyKind] = TapirCodec.derivedEnumeration[String, RallyKind](
  s => RallyKind.values.find(k => k.toString.toLowerCase == s),
  _.toString.toLowerCase,
  None
)

object Endpoints:
  val data = endpoint.in("data" / path[RallyKind] / path[String]).out(jsonBody[RallyData]).errorOut(jsonBody[ErrorInfo])
  val refresh =
    endpoint.post.in("refresh" / path[RallyKind] / path[String]).out(jsonBody[RallyData]).errorOut(jsonBody[ErrorInfo])

  val find = endpoint
    .in("find" / path[RallyKind])
    .in(query[String]("championship").and(query[Option[Int]]("year")))
    .out(jsonBody[List[RallySummary]])
    .errorOut(jsonBody[ErrorInfo])

  object Admin:
    private val base = endpoint.in("admin").securityIn(auth.basic[UsernamePassword]()).errorOut(jsonBody[ErrorInfo])
    val refresh = base.post.in("refresh").out(jsonBody[List[RefreshResult]])
    val delete = base.post.in("delete" / path[RallyKind] / path[String])

case class Stage(number: Int, name: String)

case class Driver(country: String, userName: String, realName: String)

case class DriverResult(
    stageNumber: Int,
    stagePosition: Int,
    overallPosition: Int,
    stageTimeMs: Int,
    overallTimeMs: Int,
    penaltyInsideStageMs: Int,
    penaltyOutsideStageMs: Int,
    superRally: Boolean,
    rallyFinished: Boolean,
    comment: String,
    nominal: Boolean
)

case class DriverResults(driver: Driver, results: List[DriverResult])

case class GroupResults(
    group: String,
    results: List[DriverResults]
)

case class CarResults(
    car: String,
    group: String,
    results: List[DriverResults]
)

case class RallyData(
    id: String,
    name: String,
    link: String,
    retrievedAt: Instant,
    stages: List[Stage],
    allResults: List[DriverResults],
    groupResults: List[GroupResults],
    carResults: List[CarResults]
)
