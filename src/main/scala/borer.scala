/*
 * Copyright 2023 github.com/2m/rallyeye-data/contributors
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

import io.bullet.borer.Codec
import sttp.tapir._
import sttp.tapir.Codec.JsonCodec
import sttp.tapir.DecodeResult.{Error, Value}
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError}
import sttp.tapir.Schema.SName

object TapirJsonBorer {
  def jsonBody[T: Codec: Schema]: EndpointIO.Body[String, T] = stringBodyUtf8AnyFormat(borerCodec[T])

  implicit def borerCodec[T: Codec: Schema]: JsonCodec[T] =
    sttp.tapir.Codec.json[T] { s =>
      io.bullet.borer.Json.decode(s.getBytes("UTF8")).to[T].valueEither match {
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
      }
    }(t => io.bullet.borer.Json.encode(t).toUtf8String)
}
