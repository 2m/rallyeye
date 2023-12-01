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

class ShardedSuite extends munit.FunSuite:
  import cats.effect.unsafe.implicits.global

  test("shards requests"):
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

    val start = System.nanoTime()
    val responses = result.unsafeRunSync()
    val end = System.nanoTime()
    val millisTaken = Duration.fromNanos(end - start)

    assertEquals(responses, numRequests)

    val worstCaseTaken = requestDelay * numRequests + startupDelay
    assert(millisTaken < worstCaseTaken, s"Expected < ${worstCaseTaken.toMillis}ms, actual ${millisTaken.toMillis}ms")
