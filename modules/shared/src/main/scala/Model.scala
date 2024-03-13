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

import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*

enum RallyKind:
  case Rsf, PressAuto, Ewrc

case class RallySummary(
    kind: RallyKind,
    externalId: String,
    name: String,
    retrievedAt: Instant,
    championship: List[String],
    start: LocalDate,
    end: LocalDate,
    distanceMeters: Int :| Greater[0],
    started: Int :| GreaterEqual[0],
    finished: Int :| GreaterEqual[0]
)
