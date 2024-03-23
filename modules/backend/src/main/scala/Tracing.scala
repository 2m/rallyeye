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

import scala.jdk.CollectionConverters.*

import cats.effect.Concurrent
import cats.effect.IO
import cats.effect.LiftIO
import cats.effect.kernel.Async
import cats.effect.kernel.MonadCancelThrow
import cats.effect.kernel.Resource
import org.http4s.HttpApp
import org.http4s.otel4s.middleware.ClientMiddleware
import org.http4s.otel4s.middleware.ServerMiddleware
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer

object Tracing:
  private def globalOtel[F[_]: Async: LiftIO](service: String) = OtelJava.autoConfigured[F]: builder =>
    builder.addPropertiesSupplier(() =>
      Map(
        "otel.metrics.exporter" -> "none",
        "otel.java.global-autoconfigure.enabled" -> "true",
        "otel.service.name" -> s"rallyeye-$service"
      ).asJava
    )

  private def createTracer[F[_]](otel: OtelJava[F]): F[Tracer[F]] =
    otel.tracerProvider.get("rallyeye")

  def tracer(service: String) =
    Tracing
      .globalOtel[IO](service)
      .flatMap { otel =>
        Resource.eval(Tracing.createTracer(otel))
      }

  def trace[A](entry: Tracer[IO] => Resource[IO, A]) =
    tracer(if BuildInfo.isSnapshot then "local" else "production").flatMap(entry)

  def client[F[_]: Tracer: Concurrent] = ClientMiddleware.default
    .withClientSpanName(req => s"${req.method} ${req.uri}")
    .build

  def server[F[_]: Tracer: MonadCancelThrow](f: HttpApp[F]): HttpApp[F] = ServerMiddleware.default
    .withServerSpanName(req => s"${req.method} ${req.uri}")
    .buildHttpApp(f)

extension [A, F[_]: Tracer](f: F[A])
  def traced(name: String): F[A] = Tracer[F].span(name).surround(f)
  def tracedR(name: String): Resource[F, A] = Resource.eval(traced(name))
