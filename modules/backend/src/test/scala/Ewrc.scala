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

import cats.effect.IO
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import io.github.iltotore.iron.*
import org.http4s.ember.client.EmberClientBuilder

class EwrcSuite
    extends munit.FunSuite
    with DiffxAssertions
    with IronBorerSupport
    with IronDiffxSupport
    with SnapshotSupport:
  val integration = new munit.Tag("integration")

  import cats.effect.unsafe.implicits.global

  given Diff[Entry] = Diff.derived[Entry]

  val httpClient = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  test("get rally name".tag(integration)):
    httpClient
      .use { client =>
        Ewrc.rallyName(client, "80245-forum8-rally-japan-2023")
      }
      .unsafeRunSync() match
      case Right(name) => assertEquals(name, "FORUM8 Rally Japan 2023")
      case Left(error) => fail(s"Unable to get rally name: $error")

  def checkEwrcResult[T](rally: String)(using munit.Location): Unit =
    test(s"get $rally results".tag(integration)):
      httpClient
        .use(Ewrc.rallyResults(_, rally).value)
        .unsafeRunSync() match
        case Right(results) =>
          val expected = snapshot(results, s"ewrc-$rally")
          assertEqual(results, expected)
        case Left(error) => fail(s"Unable to get ewrc results: $error")

  checkEwrcResult("80245-forum8-rally-japan-2023")
  checkEwrcResult("80244-central-european-rally-2023")
  checkEwrcResult("80239-safari-rally-kenya-2023")
  checkEwrcResult("59972-rallye-automobile-monte-carlo-2020")
