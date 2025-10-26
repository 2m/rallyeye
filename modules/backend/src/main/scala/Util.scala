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

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

val Unicode = "&#[0-9]+".r

extension (s: String)
  def decodeHtmlUnicode = Unicode.replaceAllIn(s, m => Integer.parseInt(m.group(0).drop(2)).toChar.toString)

  def toMs =
    s.match
      case ""                  => 0
      case s"$seconds.$millis" =>
        seconds.toInt * 1000 + millis.padTo(3, '0').take(3).toInt
      case s"$seconds" =>
        seconds.toInt * 1000
      case time => throw Error(s"Unable to parse milliseconds from [$s]")

  def sha256hash: String =
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(s.getBytes(StandardCharsets.UTF_8))
    val hashedBytes = digest.digest()
    hashedBytes.map("%02x".format(_)).mkString
