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

import io.bullet.borer.Codec
import io.bullet.borer.Decoder
import io.bullet.borer.Encoder
import io.github.iltotore.iron.*
import sttp.tapir.Schema
import sttp.tapir.ValidationResult
import sttp.tapir.Validator

trait IronBorerSupport:
  inline given [T: Encoder: Decoder, P](using Constraint[T, P]): Codec[T :| P] =
    Codec.bimap[T, T :| P](identity, _.refine)

// https://github.com/Iltotore/iron/discussions/119#discussioncomment-5860453
trait IronTapirSupport:
  inline def ironValidator[A, C](inline constraint: Constraint[A, C]): Validator[A] =
    Validator.custom(
      value =>
        if constraint.test(value) then ValidationResult.Valid
        else ValidationResult.Invalid(List(s"Iron constraint failed for $value: ${constraint.message}")),
      Some(constraint.message)
    )

  inline given [A, C](using inline baseSchema: Schema[A], inline constraint: Constraint[A, C]): Schema[A :| C] =
    baseSchema
      .validate(ironValidator(constraint))
      .map(value => Some[A :| C](value.assume[C]))(x => x)
