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
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

class AutorallySuite extends munit.FunSuite with SnapshotSupport:
  override val munitTimeout = 2.minutes

  given Resource[IO, Client[IO]] = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  val checkAutorallyInfo = check(Autorally.rallyInfo, "autorally-info")
  checkAutorallyInfo("504")

  val checkAutorallyResults = check(Autorally.rallyResults, "autorally-results")
  checkAutorallyResults("504")
