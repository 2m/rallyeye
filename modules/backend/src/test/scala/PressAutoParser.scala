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

class PressAutoParserSuite extends munit.FunSuite with DiffxAssertions:

  val csv =
    """|POS.;#;Name;Competitor;Group;Vehicle;SS1 Auto Bild I (Aukštadvaris);SS2 Auto Bild II (Aukštadvaris);SS3 Gold FM I (Kaunas);SS4 Gold FM II (Kaunas);SS5 Continental I (Nacionalinis žiedas);SS6 Continental II (Nacionalinis žiedas);SS7 Kėdainiai I;SS8 Kėdainiai II;SS9 Nissan I (Kuršėnai);SS10 Nissan II (Kuršėnai);LK Day 1;SS11 Melnragė I;SS12 Melnragė II;SS13 15min I (Merkio g.);SS14 15min II (Merkio g.);SS15 Febi I (Mickai);SS16 Febi III (Mickai);SS17 Inbalance I (Švepelių g. );SS18 Transeksta I (Perkėlos g.);SS19 Transeksta II (Perkėlos g.);SS20 Febi III (Mickai);SS21 Febi IV (Mickai);SS22 Inbalance II (Švepelių g.);LK Day 2;Total spent time
       |1;#107;Tomas Markelevičius - Tadas Martinaitis;15min;Press iki 2000cc;Mitsubishi Colt (2000);00:02:20.552;00:02:22.288;00:00:42.851;00:00:41.485;00:03:19.002;00:03:13.681;00:01:31.172;00:01:22.963;00:01:58.995;00:01:55.932;00:00:00.000;00:00:00.000 (N);00:00:00.000 (N);00:01:37.947;00:01:33.431;00:02:04.492;00:02:00.697;00:04:36.525;00:01:15.753;00:01:14.433;00:02:02.350;00:01:57.782;;;
       |""".stripMargin

  test("parses a CSV file"):
    val obtained = parsePressRally(csv)
    val expected = List(
      Entry(
        stageNumber = 1,
        stageName = "SS1 Auto Bild I (Aukštadvaris)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 140.552,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 2,
        stageName = "SS2 Auto Bild II (Aukštadvaris)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 142.288,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 3,
        stageName = "SS3 Gold FM I (Kaunas)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 42.851,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 4,
        stageName = "SS4 Gold FM II (Kaunas)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 41.485,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 5,
        stageName = "SS5 Continental I (Nacionalinis žiedas)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 199.002,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 6,
        stageName = "SS6 Continental II (Nacionalinis žiedas)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 193.681,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 7,
        stageName = "SS7 Kėdainiai I",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 91.172,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 8,
        stageName = "SS8 Kėdainiai II",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 82.963,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 9,
        stageName = "SS9 Nissan I (Kuršėnai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 118.995,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 10,
        stageName = "SS10 Nissan II (Kuršėnai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 115.932,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 11,
        stageName = "LK Day 1",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 0,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 12,
        stageName = "SS11 Melnragė I",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 0,
        superRally = false,
        finished = true,
        comment = "",
        nominal = true
      ),
      Entry(
        stageNumber = 13,
        stageName = "SS12 Melnragė II",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 0,
        superRally = false,
        finished = true,
        comment = "",
        nominal = true
      ),
      Entry(
        stageNumber = 14,
        stageName = "SS13 15min I (Merkio g.)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 97.947,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 15,
        stageName = "SS14 15min II (Merkio g.)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 93.431,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 16,
        stageName = "SS15 Febi I (Mickai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 124.492,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 17,
        stageName = "SS16 Febi III (Mickai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 120.697,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 18,
        stageName = "SS17 Inbalance I (Švepelių g. )",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 276.525,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 19,
        stageName = "SS18 Transeksta I (Perkėlos g.)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 75.753,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 20,
        stageName = "SS19 Transeksta II (Perkėlos g.)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 74.433,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 21,
        stageName = "SS20 Febi III (Mickai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 122.350,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 22,
        stageName = "SS21 Febi IV (Mickai)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 117.782,
        superRally = false,
        finished = true,
        comment = ""
      ),
      Entry(
        stageNumber = 23,
        stageName = "SS22 Inbalance II (Švepelių g.)",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 0,
        superRally = false,
        finished = false,
        comment = ""
      ),
      Entry(
        stageNumber = 24,
        stageName = "LK Day 2",
        userName = "Tomas Markelevičius - Tadas Martinaitis",
        group = "Press iki 2000cc",
        car = "Mitsubishi Colt (2000)",
        stageTime = 0,
        superRally = false,
        finished = false,
        comment = ""
      )
    )
    assertEqual(obtained, expected)
