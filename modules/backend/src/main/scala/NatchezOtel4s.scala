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

import java.net.URI

import cats.{~>, Monad}
import cats.effect.kernel.Resource
import cats.syntax.all.*
import natchez.{Kernel, Span, TraceValue}
import org.typelevel.ci.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.{SpanKind, SpanOps, Tracer}

// https://gist.github.com/iRevive/aab29a761a746db62edb555a47c124d2
// https://discord.com/channels/632277896739946517/1093154207328108634/1221066759315132456
object NatchezOtel4s:

  def fromOtel4s[F[_]: Monad](tracer: Tracer[F]): natchez.Trace[F] =
    new natchez.Trace[F]:
      def put(fields: (String, TraceValue)*): F[Unit] =
        for
          span <- tracer.currentSpanOrNoop
          _ <- span.addAttributes(fields.map(f => toAttribute(f._1, f._2)))
        yield ()

      // todo: you can mimic logs with 'span.addEvent'
      def log(fields: (String, TraceValue)*): F[Unit] = ???
      def log(event: String): F[Unit] = ???

      def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
        for
          span <- tracer.currentSpanOrNoop
          _ <- span.recordException(err, fields.map(f => toAttribute(f._1, f._2)))
        yield ()

      def kernel: F[Kernel] =
        for headers <- tracer.propagate(Map.empty[String, String])
        yield Kernel(headers.map { case (key, value) => CIString(key) -> value })

      def spanR(name: String, options: Span.Options): Resource[F, F ~> F] =
        makeSpan(name, options).resource.map(_.trace)

      def span[A](name: String, options: Span.Options)(k: F[A]): F[A] =
        if name.startsWith("PRAGMA") then k else makeSpan(name, options).surround(k)

      def traceId: F[Option[String]] =
        for spanContext <- tracer.currentSpanContext
        yield spanContext.map(_.traceIdHex)

      def traceUri: F[Option[URI]] =
        Monad[F].pure(None)

      private def makeSpan(name: String, options: Span.Options): SpanOps[F] =
        // todo: not implemented
        // options.parent
        // options.spanCreationPolicy
        // options.links

        val spanKind = options.spanKind match
          case natchez.Span.SpanKind.Internal => SpanKind.Internal
          case natchez.Span.SpanKind.Client   => SpanKind.Client
          case natchez.Span.SpanKind.Server   => SpanKind.Server
          case natchez.Span.SpanKind.Producer => SpanKind.Producer
          case natchez.Span.SpanKind.Consumer => SpanKind.Consumer

        tracer
          .spanBuilder(name)
          .withSpanKind(spanKind)
          .build

      private def toAttribute(key: String, value: TraceValue): Attribute[?] =
        value match
          case TraceValue.StringValue(value)  => Attribute(key, value)
          case TraceValue.BooleanValue(value) => Attribute(key, value)
          case TraceValue.NumberValue(value)  => Attribute(key, value.longValue())
