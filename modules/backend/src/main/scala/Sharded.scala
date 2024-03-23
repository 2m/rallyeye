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

import cats.Monad
import cats.effect.kernel.Concurrent
import cats.effect.kernel.Deferred
import cats.implicits.*
import fs2.Stream
import fs2.concurrent.Topic
import org.typelevel.otel4s.trace.SpanContext
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.ErrorInfo
import rallyeye.shared.RallyKind

trait Shardable[A]:
  extension (a: A) def shard(s: Int): Int

given Shardable[(RallyKind, String)] with
  extension (s: (RallyKind, String)) def shard(shard: Int): Int = s._2.hashCode % shard

case class ShardedEntry[F[_], Req, Resp](req: Req, ctx: SpanContext, resp: Deferred[F, Either[ErrorInfo, Resp]])

def shardedLogic[F[_]: Monad: Concurrent: Tracer, Req: Shardable, Resp](shards: Int)(
    logic: Req => F[Either[ErrorInfo, Resp]]
) =
  def logicPipe(shard: Int)(stream: Stream[F, ShardedEntry[F, Req, Resp]]) =
    stream
      .filter(_.req.shard(shards) == shard)
      .evalMap: entry =>
        for
          resp <- Tracer[F].childScope(entry.ctx):
            logic(entry.req).traced(s"sharded-worker-$shard")
          _ <- entry.resp.complete(resp)
        yield ()

  val ShardQueueLength = 50
  for
    topic <- Topic[F, ShardedEntry[F, Req, Resp]]
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
        deferredResponse <- Deferred[F, Either[ErrorInfo, Resp]]
        span <- Tracer[F].currentSpanOrNoop
        _ <- span.addEvent("Adding request to sharded queue")
        _ <- topic.publish1(ShardedEntry(request, span.context, deferredResponse))
        response <- deferredResponse.get
      yield response
  )
