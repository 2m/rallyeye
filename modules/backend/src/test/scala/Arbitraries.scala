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

import scala.collection.immutable.ArraySeq

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.scalacheck.numeric.given
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.ops.*
import rallyeye.shared.RallyKind
import rallyeye.storage.Rally
import rallyeye.storage.Result

trait Arbitraries:

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
