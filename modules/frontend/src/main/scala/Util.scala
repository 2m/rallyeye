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

import java.time.Duration
import java.time.Instant

import scala.concurrent.duration.*

extension (d: BigDecimal)
  def padTo(n: Int)(i: Int) = i.toString.reverse.padTo(n, '0').reverse

  def prettyDuration: String =
    val duration = Duration.ofMillis((d * 1000).toLong)
    val ms = padTo(3)(duration.toMillisPart)
    val s = padTo(2)(duration.toSecondsPart)
    val m = padTo(2)(duration.toMinutesPart)
    val h = padTo(2)(duration.toHoursPart)

    val parts = List(s"${ms}ms", s"${s}s", s"${m}m")
    val optionalHourPart = if h != "00" then List(s"${h}h") else List.empty

    (parts ::: optionalHourPart).reverse.mkString("")

  def prettyDiff: String =
    val duration = Duration.ofMillis((d * 1000).toLong)
    val ms = padTo(3)(duration.toMillisPart)
    val s = padTo(2)(duration.toSecondsPart)
    val m = padTo(2)(duration.toMinutesPart)
    val h = padTo(2)(duration.toHoursPart)

    return s"$h:$m:$s.$ms"

extension (i: Instant)
  def prettyAgo: String =
    val duration = Duration.between(i, Instant.now())
    FiniteDuration(duration.toMillis, MILLISECONDS) match
      case d if d < 1.minute  => "just now"
      case d if d < 2.minutes => "a minute ago"
      case d if d < 1.hour    => s"${d.toMinutes} minutes ago"
      case d if d < 2.hours   => "an hour ago"
      case d if d < 1.day     => s"${d.toHours} hours ago"
      case d if d < 2.days    => "a day ago"
      case d                  => s"${d.toDays} days ago"
