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
import io.bullet.borer.Codec
import io.bullet.borer.derivation.MapBasedCodecs.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.model.UsernamePassword

type Endpoint = sttp.tapir.Endpoint[Unit, String, ErrorInfo, RallyData, Any]

sealed trait ErrorInfo
case class GenericError(message: String) extends ErrorInfo
case class RallyNotStored() extends ErrorInfo
case class RallyInProgress() extends ErrorInfo

object Endpoints:
  object Rsf:
    private val base = endpoint.in("rsf" / path[String]).errorOut(jsonBody[ErrorInfo])
    val data = base.out(jsonBody[RallyData])
    val refresh = base.post.in("refresh").out(jsonBody[RallyData])

  object PressAuto:
    val data = endpoint
      .in("pressauto" / path[String])
      .out(jsonBody[RallyData])
      .errorOut(jsonBody[ErrorInfo])

  object Ewrc:
    private val base = endpoint.in("ewrc" / path[String]).errorOut(jsonBody[ErrorInfo])
    val data = base.out(jsonBody[RallyData])
    val refresh = base.post.in("refresh").out(jsonBody[RallyData])

  object Admin:
    private val base = endpoint.in("admin").securityIn(auth.basic[UsernamePassword]()).errorOut(jsonBody[ErrorInfo])
    val refresh = base.post.in("refresh")

    object Rsf:
      val delete = base.post.in("rsf" / path[String])

    object Ewrc:
      val delete = base.post.in("ewrc" / path[String])

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

given Codec[Stage] = deriveCodec[Stage]
given Codec[Driver] = deriveCodec[Driver]
given Codec[DriverResult] = deriveCodec[DriverResult]
given Codec[DriverResults] = deriveCodec[DriverResults]
given Codec[GroupResults] = deriveCodec[GroupResults]
given Codec[CarResults] = deriveCodec[CarResults]
given Codec[RallyData] = deriveCodec[RallyData]

given Codec[GenericError] = deriveCodec[GenericError]
given Codec[RallyNotStored] = deriveCodec[RallyNotStored]
given Codec[RallyInProgress] = deriveCodec[RallyInProgress]
given Codec[ErrorInfo] = deriveCodec[ErrorInfo]
