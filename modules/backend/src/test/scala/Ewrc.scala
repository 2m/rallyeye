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

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.kernel.Resource
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

class EwrcSuite extends munit.FunSuite with SnapshotSupport:

  override val munitTimeout = 2.minutes

  given Resource[IO, Client[IO]] = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  val checkEwrcInfo = check(Ewrc.rallyInfo, "ewrc-info")
  checkEwrcInfo("80245-forum8-rally-japan-2023")
  checkEwrcInfo("80244-central-european-rally-2023")
  checkEwrcInfo("80239-safari-rally-kenya-2023")
  checkEwrcInfo("59972-rallye-automobile-monte-carlo-2020")

  val checkEwrcResult = check(Ewrc.rallyResults, "ewrc-results")
  checkEwrcResult("80245-forum8-rally-japan-2023")
  checkEwrcResult("80244-central-european-rally-2023")
  checkEwrcResult("80239-safari-rally-kenya-2023")
  checkEwrcResult("59972-rallye-automobile-monte-carlo-2020")
  checkEwrcResult("81364-alburnus-rally-elektrenai-2023")
