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
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.effect.syntax.all.*
import cats.effect.unsafe.IORuntime
import cats.implicits.*
import org.http4s.RequestPrelude
import org.http4s.Uri
import org.http4s.otel4s.middleware.client.UriTemplateClassifier
import org.http4s.otel4s.middleware.server.RouteClassifier
import org.http4s.otel4s.middleware.trace.client.ClientMiddleware
import org.http4s.otel4s.middleware.trace.client.ClientSpanDataProvider
import org.http4s.otel4s.middleware.trace.client.UriRedactor
import org.http4s.otel4s.middleware.trace.redact.HeaderRedactor
import org.http4s.otel4s.middleware.trace.server.ServerMiddleware
import org.http4s.otel4s.middleware.trace.server.ServerSpanDataProvider
import org.typelevel.ci.CIString
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.instrumentation.ce.IORuntimeMetrics
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.oteljava.OtelJava
import org.typelevel.otel4s.oteljava.context.Context
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.trace.TracerProvider

object Telemetry:
  final val App = "rallyeye"

  private def globalOtel[F[_]: Async: LiftIO](environment: String) = OtelJava.autoConfigured[F]: builder =>
    builder.addPropertiesSupplier(() =>
      (Map(
        "otel.java.global-autoconfigure.enabled" -> "true",
        "otel.service.name" -> s"$App-$environment"
      ) ++ sys.env
        .get("HONEYCOMB_API_KEY")
        .fold(Map())(key =>
          Map(
            "otel.exporter.otlp.endpoint" -> "https://api.honeycomb.io/",
            "otel.exporter.otlp.headers" -> s"x-honeycomb-team=$key,x-honeycomb-dataset=otel-metrics"
          )
        )).asJava
    )

  private def serviceName = if BuildInfo.isSnapshot then "local" else "production"

  def instruments(service: String) =
    for
      otel <- globalOtel[IO](service)
      provider = otel.tracerProvider
      tracer <- provider.get(App).toResource
      (given MeterProvider[IO]) = otel.meterProvider
      meter <- summon[MeterProvider[IO]].get(App).toResource
      _ <- IORuntimeMetrics.register[IO](IORuntime.global.metrics, IORuntimeMetrics.Config.default)
    yield (provider, tracer, meter)

  def instrument[A](entry: (TracerProvider[IO], Tracer[IO], Meter[IO]) ?=> Resource[IO, A]) =
    for
      (given TracerProvider[IO], given Tracer[IO], given Meter[IO]) <- instruments(serviceName)
      results <- entry
    yield results

  def tracedClient[F[_]: TracerProvider: Concurrent] = ClientMiddleware
    .builder(
      ClientSpanDataProvider
        .openTelemetry(new UriRedactor.OnlyRedactUserInfo {})
        .withUrlTemplateClassifier(
          new UriTemplateClassifier:
            override def classify(uri: Uri): Option[String] = Some(uri.path.toString)
        )
        .optIntoHttpResponseHeaders(HeaderRedactor.default)
        .optIntoUrlScheme
        .optIntoUrlTemplate
    )
    .build

  def tracedServer[F[_]: TracerProvider: Concurrent] = ServerMiddleware
    .builder[F](
      ServerSpanDataProvider
        .openTelemetry(new UriRedactor.OnlyRedactUserInfo {})
        .withRouteClassifier(
          new RouteClassifier:
            override def classify(request: RequestPrelude): Option[String] = Some(request.uri.path.toString)
        )
        .optIntoClientPort
        .optIntoHttpRequestHeaders(
          HeaderRedactor.default.copy(headersAllowedUnredacted =
            HeaderRedactor.default.headersAllowedUnredacted ++ Set(
              "Fly-Client-IP",
              "Fly-Forwarded-Port",
              "Fly-Region"
            ).map(CIString(_))
          )
        )
    )
    .build

extension [A, F[_]: Tracer](f: F[A])
  def traced(name: String, attributes: Attribute[?]*): F[A] = Tracer[F].span(name, attributes).surround(f)
  def tracedR(name: String): Resource[F, A] = traced(name).toResource

extension [A, F[_]: Tracer: Sync](r: Resource[F, A])
  def rootSpan(name: String): Resource[F, A] = Resource.eval(Tracer[F].rootSpan(name).surround(r.use(_.pure[F])))
