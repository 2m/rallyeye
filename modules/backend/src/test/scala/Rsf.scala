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

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.*
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import munit.CatsEffectSuite
import org.http4s.Response
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder

class RsfSuite extends munit.FunSuite with SnapshotSupport:

  given Resource[IO, Client[IO]] = EmberClientBuilder
    .default[IO]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build

  val checkRsfInfo = check(Rsf.rallyInfo, "rsf-info")
  checkRsfInfo("59862")
  checkRsfInfo("59247")
  checkRsfInfo("66003")

  val checkRsfResult = check(Rsf.rallyResults, "rsf-results")
  checkRsfResult("58147")
  checkRsfResult("65713")

class RsfParserSuite extends CatsEffectSuite with SnapshotSupport:
  def httpClient[F[_]: Files: Concurrent](response: Stream[F, Byte]) = Client[F]: _ =>
    Resource.eval(Response[F](body = response).pure[F])

  test("parses csv response"):
    val response =
      Files[IO].readAll(Path(BuildInfo.test_resourceDirectory.toPath().resolve("rsf-local-65713.csv").toString))
    Rsf
      .rallyResults(httpClient[IO](response), "")
      .value
      .map:
        case Right(obtained) =>
          val expected = snapshot(obtained, "rsf-local-65713")
          assertEquals(obtained, expected)
        case _ => fail("Unable to parse CSV")

  test("handles ongoing rally"):
    val response = Stream.emits("The rally is not over yet.".getBytes)
    Rsf.rallyResults(httpClient[IO](response), "").value.map(r => assertEquals(r, Left(Logic.RallyInProgress)))
