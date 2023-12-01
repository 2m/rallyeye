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

import java.nio.file.Files
import java.time.Instant

import cats.effect.IO
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import io.bullet.borer.Codec
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.Json
import io.bullet.borer.derivation.MapBasedCodecs.*
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

  given Codec[Instant] = Codec.bimap[Long, Instant](_.getEpochSecond, Instant.ofEpochSecond)
  given Codec[Entry] = deriveCodec[Entry]

  given Diff[Entry] = Diff.derived[Entry]

  val httpClient = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  test("get rally name".tag(integration).ignore):
    httpClient
      .use { client =>
        Ewrc.rallyName(client, "80245-forum8-rally-japan-2023")
      }
      .unsafeRunSync() match
      case Right(name) => assertEquals(name, "FORUM8 Rally Japan 2023")
      case Left(error) => fail(s"Unable to get rally name: $error")

  test("get rally japan 2023 results".tag(integration).ignore):
    httpClient
      .use { client =>
        Ewrc.rallyResults(client, "80245-forum8-rally-japan-2023").value
      }
      .unsafeRunSync() match
      case Right(results) =>
        val expected = snapshot(results, "rally-japan-2023")
        assertEqual(results, expected)
      case Left(error) => fail(s"Unable to get rally results: $error")

  test("get central european rally 2023 results".tag(integration).ignore):
    httpClient
      .use { client =>
        Ewrc.rallyResults(client, "80244-central-european-rally-2023").value
      }
      .unsafeRunSync() match
      case Right(results) =>
        val expected = snapshot(results, "central-european-rally-2023")
        assertEqual(results, expected)
      case Left(error) => fail(s"Unable to get rally results: $error")

  test("get central safari rally kenya 2023 results".tag(integration)):
    httpClient
      .use { client =>
        Ewrc.rallyResults(client, "80239-safari-rally-kenya-2023").value
      }
      .unsafeRunSync() match
      case Right(results) =>
        val expected = snapshot(results, "central-safari-rally-kenya-2023")
        assertEqual(results, expected)
      case Left(error) => fail(s"Unable to get rally results: $error")

trait SnapshotSupport:
  def snapshot[A: Encoder: Decoder](value: A, snapshotName: String): A =
    val resultJson = Json.encode(value).toUtf8String
    Files.writeString(
      BuildInfo.test_resourceDirectory.toPath().resolve(snapshotName + ".json.new"),
      resultJson
    )
    Json
      .decode(
        Files.readString(BuildInfo.test_resourceDirectory.toPath().resolve(snapshotName + ".json")).getBytes
      )
      .to[A]
      .value
