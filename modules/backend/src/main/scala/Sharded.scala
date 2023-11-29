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

import cats.effect.IO
import cats.effect.kernel.Deferred
import fs2.Stream
import fs2.concurrent.Topic
import rallyeye.shared.ErrorInfo

trait Shardable[A]:
  extension (a: A) def shard(s: Int): Int

given Shardable[Int] with
  extension (i: Int)
    def shard(s: Int): Int =
      i % s

def shardedLogic[Req: Shardable, Resp](shards: Int)(logic: Req => IO[Either[ErrorInfo, Resp]]) =
  def logicPipe(shard: Int)(stream: Stream[IO, (Req, Deferred[IO, Either[ErrorInfo, Resp]])]) =
    stream
      .filter((req, _) => req.shard(shards) == shard)
      .evalMap((req, deferredResponse) => logic(req).flatMap(deferredResponse.complete).map(_ => ()))

  val ShardQueueLength = 50
  for
    topic <- Topic[IO, (Req, Deferred[IO, Either[ErrorInfo, Resp]])]
    shardStreams = Stream
      .emits(
        List
          .range(0, shards)
          .map(shard => topic.subscribe(ShardQueueLength).through(logicPipe(shard)))
      )
      .parJoin(shards)
  yield (
    shardStreams,
    (request: Req) =>
      for
        deferredResponse <- Deferred[IO, Either[ErrorInfo, Resp]]
        published <- topic.publish1((request, deferredResponse))
        response <- deferredResponse.get
      yield response
  )
