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

import java.time.Instant

import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import io.github.iltotore.iron.*
import rallyeye.shared.*
import rallyeye.storage.Rally
import rallyeye.storage.RallyKind

class ResultsSuite extends munit.FunSuite with DiffxAssertions:
  given Diff[CarResults] = Diff.derived[CarResults]
  given Diff[GroupResults] = Diff.derived[GroupResults]
  given Diff[DriverResult] = Diff.derived[DriverResult]
  given Diff[DriverResults] = Diff.derived[DriverResults]
  given Diff[Stage] = Diff.derived[Stage]
  given Diff[Driver] = Diff.derived[Driver]

  val entries = List(
    Entry(
      1,
      "SS1",
      "LT",
      "driver1",
      "name1",
      "group1",
      "car1",
      None,
      None,
      10.1,
      None,
      None,
      None,
      false,
      true,
      "good stage"
    ),
    Entry(
      1,
      "SS1",
      "LT",
      "driver2",
      "name2",
      "group1",
      "car2",
      None,
      None,
      14.9,
      None,
      None,
      None,
      false,
      true,
      "good stage"
    ),
    Entry(
      2,
      "SS2",
      "LT",
      "driver1",
      "name1",
      "group1",
      "car1",
      None,
      None,
      20.5,
      None,
      None,
      None,
      false,
      true,
      "good stage"
    ),
    Entry(
      2,
      "SS2",
      "LT",
      "driver2",
      "name2",
      "group1",
      "car2",
      None,
      None,
      24.5,
      None,
      None,
      None,
      false,
      true,
      "good stage"
    )
  )

  test("gives results"):
    val obtained = results(entries).toMap
    val expected = Map(
      Stage(1, "SS1") -> List(
        PositionResult(1, "LT", "driver1", "name1", 1, 1, 10.1, 10.1, false, true, "good stage", false),
        PositionResult(1, "LT", "driver2", "name2", 2, 2, 14.9, 14.9, false, true, "good stage", false)
      ),
      Stage(2, "SS2") -> List(
        PositionResult(2, "LT", "driver1", "name1", 1, 1, 20.5, 30.6, false, true, "good stage", false),
        PositionResult(2, "LT", "driver2", "name2", 2, 2, 24.5, 39.4, false, true, "good stage", false)
      )
    )

    assertEquals(obtained, expected)

  test("gives rally results"):
    val rally = Rally(RallyKind.Rsf, "1", "rally", Instant.now)
    val obtained = rallyData(rally, entries)
    val expected = RallyData(
      rally.externalId.toInt,
      rally.name,
      rally.kind.link(rally),
      rally.retrievedAt,
      List(
        Stage(1, "SS1"),
        Stage(2, "SS2")
      ),
      List(
        DriverResults(
          Driver("LT", "driver1", "name1"),
          List(
            DriverResult(1, 1, 1, 10.1, 10.1, false, true, "good stage", false),
            DriverResult(2, 1, 1, 20.5, 30.6, false, true, "good stage", false)
          )
        ),
        DriverResults(
          Driver("LT", "driver2", "name2"),
          List(
            DriverResult(1, 2, 2, 14.9, 14.9, false, true, "good stage", false),
            DriverResult(2, 2, 2, 24.5, 39.4, false, true, "good stage", false)
          )
        )
      ),
      List(
        GroupResults(
          "group1",
          List(
            DriverResults(
              Driver("LT", "driver1", "name1"),
              List(
                DriverResult(1, 1, 1, 10.1, 10.1, false, true, "good stage", false),
                DriverResult(2, 1, 1, 20.5, 30.6, false, true, "good stage", false)
              )
            ),
            DriverResults(
              Driver("LT", "driver2", "name2"),
              List(
                DriverResult(1, 2, 2, 14.9, 14.9, false, true, "good stage", false),
                DriverResult(2, 2, 2, 24.5, 39.4, false, true, "good stage", false)
              )
            )
          )
        )
      ),
      List(
        CarResults(
          "car2",
          "group1",
          List(
            DriverResults(
              Driver("LT", "driver2", "name2"),
              List(
                DriverResult(1, 1, 1, 14.9, 14.9, false, true, "good stage", false),
                DriverResult(2, 1, 1, 24.5, 39.4, false, true, "good stage", false)
              )
            )
          )
        ),
        CarResults(
          "car1",
          "group1",
          List(
            DriverResults(
              Driver("LT", "driver1", "name1"),
              List(
                DriverResult(1, 1, 1, 10.1, 10.1, false, true, "good stage", false),
                DriverResult(2, 1, 1, 20.5, 30.6, false, true, "good stage", false)
              )
            )
          )
        )
      )
    )

    assertEqual(obtained.stages, expected.stages)
    assertEqual(obtained.allResults, expected.allResults)
    assertEqual(obtained.groupResults, expected.groupResults)
    assertEqual(obtained.carResults, expected.carResults)
