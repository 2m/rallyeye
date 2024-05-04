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

import cats.data.EitherT
import cats.effect.IO
import cats.effect.kernel.Resource
import com.softwaremill.diffx.Diff
import com.softwaremill.diffx.munit.DiffxAssertions
import io.bullet.borer.Codec
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.Json
import io.bullet.borer.derivation.MapBasedCodecs.*
import io.github.iltotore.iron.borer.given
import org.http4s.client.Client
import rallyeye.shared.Codecs.given

trait SnapshotSupport extends IronDiffxSupport, DiffxAssertions:
  this: munit.FunSuite =>

  val integration = new munit.Tag("integration")

  given Codec[RallyInfo] = deriveCodec[RallyInfo]
  given Codec[Entry] = deriveCodec[Entry]

  given Diff[RallyInfo] = Diff.derived[RallyInfo]
  given Diff[Entry] = Diff.derived[Entry]

  def check[T](
      fun: (Client[IO], String) => EitherT[IO, Throwable, T],
      tag: String
  )(rally: String)(using munit.Location, Encoder[T], Decoder[T], Diff[T], Resource[IO, Client[IO]]): Unit =
    import cats.effect.unsafe.implicits.global

    val httpClient = summon[Resource[IO, Client[IO]]]
    test(s"get $rally $tag".tag(integration)):
      httpClient
        .use(fun(_, rally).value)
        .unsafeRunSync() match
        case Right(results) =>
          val expected = snapshot(results, s"$tag-$rally")
          assertEqual(results, expected)
        case Left(error) => fail(s"Unable to get $tag: $error", error)

  def snapshot[A](value: A, snapshotName: String)(using Encoder[A], Decoder[A]): A =
    val resultJson = Json.encode(value).toUtf8String
    Files.writeString(
      BuildInfo.test_resourceDirectory.toPath().resolve(snapshotName + ".new.json"),
      resultJson
    )
    Json
      .decode(
        Files.readString(BuildInfo.test_resourceDirectory.toPath().resolve(snapshotName + ".json")).getBytes
      )
      .to[A]
      .value
