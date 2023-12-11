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

import scala.io.Codec
import scala.io.Source

import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import io.github.iltotore.iron.*

class PressAutoSuite extends munit.FunSuite with DiffxAssertions with IronDiffxSupport with SnapshotSupport:
  given Diff[Entry] = Diff.derived[Entry]

  val csv =
    """|POS.;#;Name;Competitor;Group;Vehicle;SS1 Auto Bild I (Aukštadvaris);SS2 Auto Bild II (Aukštadvaris);SS3 Gold FM I (Kaunas);SS4 Gold FM II (Kaunas);SS5 Continental I (Nacionalinis žiedas);SS6 Continental II (Nacionalinis žiedas);SS7 Kėdainiai I;SS8 Kėdainiai II;SS9 Nissan I (Kuršėnai);SS10 Nissan II (Kuršėnai);LK Day 1;SS11 Melnragė I;SS12 Melnragė II;SS13 15min I (Merkio g.);SS14 15min II (Merkio g.);SS15 Febi I (Mickai);SS16 Febi III (Mickai);SS17 Inbalance I (Švepelių g. );SS18 Transeksta I (Perkėlos g.);SS19 Transeksta II (Perkėlos g.);SS20 Febi III (Mickai);SS21 Febi IV (Mickai);SS22 Inbalance II (Švepelių g.);LK Day 2;Total spent time
       |1;#107;Tomas Markelevičius - Tadas Martinaitis;15min;Press iki 2000cc;Mitsubishi Colt (2000);00:02:20.552;00:02:22.288;00:00:42.851;00:00:41.485;00:03:19.002;00:03:13.681;00:01:31.172;00:01:22.963;00:01:58.995;00:01:55.932;00:00:00.000;00:00:00.000 (N);00:00:00.000 (N);00:01:37.947;00:01:33.431;00:02:04.492;00:02:00.697;00:04:36.525;00:01:15.753;00:01:14.433;00:02:02.350;00:01:57.782;;;
       |""".stripMargin

  test("parses single driver result"):
    val obtained = PressAuto.parseResults(csv)
    val expected = snapshot(obtained, "press-auto-single-driver")
    assertEqual(obtained, expected)

  test("parses all results"):
    val csv = Source.fromResource("pressauto2023.csv")(Codec.UTF8).mkString
    val obtained = PressAuto.parseResults(csv)
    assertEqual(obtained.size, 1632)
