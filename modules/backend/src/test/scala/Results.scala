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

import com.softwaremill.diffx.generic.auto.given
import com.softwaremill.diffx.munit.DiffxAssertions
import rallyeye.shared._

class ResultsSuite extends munit.FunSuite with DiffxAssertions:
  val entries = List(
    Entry(1, "SS1", "driver1", "group1", "car1", 10.1, false, true, "good stage"),
    Entry(1, "SS1", "driver2", "group1", "car2", 14.9, false, true, "good stage"),
    Entry(2, "SS2", "driver1", "group1", "car1", 20.5, false, true, "good stage"),
    Entry(2, "SS2", "driver2", "group1", "car2", 24.5, false, true, "good stage")
  )

  test("gives results"):
    val obtained = results(entries).toMap
    val expected = Map(
      Stage(1, "SS1") -> List(
        PositionResult(1, "driver1", 1, 1, 10.1, 10.1, false, true, "good stage", false),
        PositionResult(1, "driver2", 2, 2, 14.9, 14.9, false, true, "good stage", false)
      ),
      Stage(2, "SS2") -> List(
        PositionResult(2, "driver1", 1, 1, 20.5, 30.6, false, true, "good stage", false),
        PositionResult(2, "driver2", 2, 2, 24.5, 39.4, false, true, "good stage", false)
      )
    )

    assertEquals(obtained, expected)

  test("gives rally results"):
    val obtained = rally(1, "rally", entries)
    val expected = RallyData(
      1,
      "rally",
      obtained.retrievedAt,
      List(
        Stage(1, "SS1"),
        Stage(2, "SS2")
      ),
      List(
        DriverResults(
          "driver1",
          List(
            PositionResult(1, "driver1", 1, 1, 10.1, 10.1, false, true, "good stage", false),
            PositionResult(2, "driver1", 1, 1, 20.5, 30.6, false, true, "good stage", false)
          )
        ),
        DriverResults(
          "driver2",
          List(
            PositionResult(1, "driver2", 2, 2, 14.9, 14.9, false, true, "good stage", false),
            PositionResult(2, "driver2", 2, 2, 24.5, 39.4, false, true, "good stage", false)
          )
        )
      ),
      List(
        GroupResults(
          "group1",
          List(
            DriverResults(
              "driver1",
              List(
                PositionResult(1, "driver1", 1, 1, 10.1, 10.1, false, true, "good stage", false),
                PositionResult(2, "driver1", 1, 1, 20.5, 30.6, false, true, "good stage", false)
              )
            ),
            DriverResults(
              "driver2",
              List(
                PositionResult(1, "driver2", 2, 2, 14.9, 14.9, false, true, "good stage", false),
                PositionResult(2, "driver2", 2, 2, 24.5, 39.4, false, true, "good stage", false)
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
              "driver2",
              List(
                PositionResult(1, "driver2", 1, 1, 14.9, 14.9, false, true, "good stage", false),
                PositionResult(2, "driver2", 1, 1, 24.5, 39.4, false, true, "good stage", false)
              )
            )
          )
        ),
        CarResults(
          "car1",
          "group1",
          List(
            DriverResults(
              "driver1",
              List(
                PositionResult(1, "driver1", 1, 1, 10.1, 10.1, false, true, "good stage", false),
                PositionResult(2, "driver1", 1, 1, 20.5, 30.6, false, true, "good stage", false)
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
