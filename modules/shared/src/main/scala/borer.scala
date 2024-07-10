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

package rallyeye.shared

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import io.bullet.borer.Codec
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.bullet.borer.derivation.MapBasedCodecs.*
import io.github.iltotore.iron.borer.given
import sttp.tapir.*
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}

object TapirJsonBorer:
  def jsonBody[T: Encoder: Decoder: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(borerCodec[T])

  implicit def borerCodec[T: Encoder: Decoder: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.bullet.borer.Json.decode(s.getBytes("UTF8")).to[T].valueEither match
        case Right(v) => Value(v)
        case Left(borerError) =>
          val tapirJsonError = JsonError(borerError.getMessage, path = List.empty)

          Error(
            original = s,
            error = JsonDecodeException(
              errors = List(tapirJsonError),
              underlying = borerError
            )
          )
    }(t => io.bullet.borer.Json.encode(t).toUtf8String)

trait BorerCodecs extends IronTapirSupport:
  given Encoder[Instant] = Encoder[Long].contramap(_.getEpochSecond)
  given Decoder[Instant] = Decoder[Long].map(Instant.ofEpochSecond)

  given Encoder[LocalDate] = Encoder[Long].contramap(_.atStartOfDay(ZoneOffset.UTC).toInstant.getEpochSecond)
  given Decoder[LocalDate] = Decoder[Long].map(l => Instant.ofEpochSecond(l).atZone(ZoneOffset.UTC).toLocalDate)

  given Codec[Stage] = deriveCodec[Stage]
  given Codec[Driver] = deriveCodec[Driver]
  given Codec[DriverResult] = deriveCodec[DriverResult]
  given Codec[DriverResults] = deriveCodec[DriverResults]
  given Codec[GroupResults] = deriveCodec[GroupResults]
  given Codec[CarResults] = deriveCodec[CarResults]
  given Codec[RallyData] = deriveCodec[RallyData]

  given Codec[GenericError] = deriveCodec[GenericError]
  given Codec[RallyNotStored] = deriveCodec[RallyNotStored]
  given Codec[RallyInProgress] = deriveCodec[RallyInProgress]
  given Codec[RefreshNotSupported] = deriveCodec[RefreshNotSupported]
  given Codec[ErrorInfo] = deriveCodec[ErrorInfo]

  given Codec[RallyKind] = deriveCodec[RallyKind]
  given Codec[RallySummary] = deriveCodec[RallySummary]
  given Codec[RefreshResult] = deriveCodec[RefreshResult]

object Codecs extends BorerCodecs
