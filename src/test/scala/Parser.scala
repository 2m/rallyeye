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

import utest._

object ParserTests extends TestSuite {
  val tests = Tests {
    test("parses single driver") {
      val entries =
        List(
          Entry(1, "SS1", "D1", Some(1), None, false, true),
          Entry(2, "SS2", "D1", Some(1), None, false, true),
          Entry(3, "SS3", "D1", Some(1), None, false, true),
          Entry(4, "SS4", "D1", Some(1), None, false, true)
        )
      val expectedResults = Map(
        Stage(1, "SS1") -> List(Driver("D1", List(Result(1, 1)))),
        Stage(2, "SS2") -> List(Driver("D1", List(Result(1, 1)))),
        Stage(3, "SS3") -> List(Driver("D1", List(Result(1, 1)))),
        Stage(4, "SS4") -> List(Driver("D1", List(Result(1, 1))))
      )
      fromEntries(entries).toMap ==> expectedResults

      test("extracts stages") {
        getStages(expectedResults.view).toList ==> List(
          Stage(1, "SS1"),
          Stage(2, "SS2"),
          Stage(3, "SS3"),
          Stage(4, "SS4")
        )
      }

      test("extracts drivers") {
        getDrivers(expectedResults.view).toList ==> List(
          Driver("D1", List(Result(1, 1), Result(1, 1), Result(1, 1), Result(1, 1)))
        )
      }
    }

    test("parses two drivers") {
      val entries =
        List(
          Entry(1, "SS1", "D1", Some(1), None, false, true),
          Entry(1, "SS1", "D2", Some(3), None, false, true),
          Entry(2, "SS2", "D2", Some(1), None, false, true),
          Entry(2, "SS2", "D1", Some(2), None, false, true)
        )
      val expectedResults = Map(
        Stage(1, "SS1") -> List(Driver("D1", List(Result(1, 1))), Driver("D2", List(Result(2, 2)))),
        Stage(2, "SS2") -> List(Driver("D1", List(Result(2, 1))), Driver("D2", List(Result(1, 2))))
      )
      fromEntries(entries).toMap ==> expectedResults

      test("extracts stages") {
        getStages(expectedResults.view).toList ==> List(
          Stage(1, "SS1"),
          Stage(2, "SS2")
        )
      }

      test("extracts drivers") {
        getDrivers(expectedResults.view).toList ==> List(
          Driver("D1", List(Result(1, 1), Result(2, 1))),
          Driver("D2", List(Result(2, 2), Result(1, 2)))
        )
      }
    }
  }
}
