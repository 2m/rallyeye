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

import io.github.iltotore.iron.*

object PressAuto:
  def parseResults(csv: String) =
    def parseTimestamp(ts: String) = ts.replace(" (N)", "") match
      case ""              => 0
      case s"$h:$m:$s.$ms" => (h.toInt * 3600 + m.toInt * 60 + s.toInt) * 1000 + ms.toInt
      case time            => throw Error(s"Unable to parse [$ts] timestamp")

    val (header :: data) = csv.split('\n').toList: @unchecked
    val stages = header.split(";", -1).drop(6).init.zipWithIndex
    data.map(_.split(";", -1).toList).flatMap {
      case country :: _ :: realName :: _ :: group :: car :: times =>
        times.init.zip(stages).map { case (time, (stageName, stageNumber)) =>
          Entry(
            (stageNumber + 1).refineUnsafe,
            stageName,
            country.toUpperCase,
            realName,
            "",
            group,
            car,
            None,
            None,
            parseTimestamp(time).refineUnsafe,
            None,
            0,
            0,
            false,
            time != "",
            None,
            time.contains("(N)") || stageName.contains("LK Day")
          )
        }
      case _ => ???
    }
