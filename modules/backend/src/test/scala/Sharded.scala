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

import cats.effect.*
import cats.syntax.all.*
import munit.CatsEffectSuite
import munit.ScalaCheckEffectSuite
import org.scalacheck.effect.PropF
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.RallyKind

class ShardedSuite extends CatsEffectSuite:
  given Shardable[String] with
    extension (s: String) def shard(shard: Int): Int = s.hashCode.abs % shard

  val traced = ResourceFunFixture: options =>
    Telemetry
      .instruments("test")
      .map:
        case (given Tracer[IO], given Meter[IO]) =>
          (test: (Tracer[IO], Meter[IO]) ?=> IO[Any]) => summon[Tracer[IO]].rootSpan(options.name).surround(test)

  traced.test("shards requests"):
    _.apply:
      val requestDelay = 100.millis
      val startupDelay = 500.millis
      val numShards = 5
      val numRequests = 20

      def delay(req: String) = Temporal[IO].sleep(requestDelay) >> IO.pure(Right(req))

      val result = for
        shardedStreamAndLogic <- shardedLogic(numShards)(delay)
        (shardedStream, logic) = shardedStreamAndLogic
        shardedStreamFiber <- shardedStream.compile.drain.start
        _ <- Temporal[IO].sleep(startupDelay)
        fibers <- (0 until numRequests).map(_.toString).toList.map(logic).traverse(_.start)
        results <- fibers.traverse(_.join)
        _ <- shardedStreamFiber.cancel
      yield results.filter(_.isSuccess).size

      for
        (taken, responses) <- Temporal[IO].timed(result)
        _ = assertEquals(responses, numRequests)

        worstCaseTaken = requestDelay * numRequests + startupDelay
        _ = assert(
          taken < worstCaseTaken,
          s"Expected < ${worstCaseTaken.toMillis}ms, actual ${taken.toMillis}ms"
        )
      yield ()

class ShardableSuite extends CatsEffectSuite with ScalaCheckEffectSuite with Arbitraries:

  test("should generate correct shard number"):
    PropF.forAllF: (kind: RallyKind, name: String) =>
      val shards = 0 until NumRefreshShards
      val req = (kind, name)
      IO(req.shard(NumRefreshShards)).map(shard =>
        assert(shards.contains(shard), s"Expected one of $shards, actual $shard")
      )
