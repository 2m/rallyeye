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

package components

import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.*
import rallyeye.shared.ErrorInfo
import rallyeye.shared.GenericError
import rallyeye.shared.RallyInProgress
import rallyeye.shared.RallyNotStored
import rallyeye.shared.RefreshNotSupported

case class Alert(errorInfoSignal: Signal[Option[ErrorInfo]]):
  def render() = div(child <-- errorInfoSignal.map:
    case Some(errorInfo) =>
      div(
        cls := "bg-orange-100 border-l-4 border-orange-500 text-orange-700 p-4",
        role := "alert",
        p(cls := "font-bold", errorTitle(errorInfo)),
        p(errorMessage(errorInfo))
      )
    case None => emptyNode)

  private def errorTitle(errorInfo: ErrorInfo) = errorInfo match
    case GenericError(message) => "Error"
    case RallyNotStored()      => ""
    case RallyInProgress()     => "Rally still in progress"
    case RefreshNotSupported() => "Refresh not available"

  private def errorMessage(errorInfo: ErrorInfo) = errorInfo match
    case GenericError(message) => message
    case RallyNotStored()      => ""
    case RallyInProgress()     =>
      "We are waiting for the rally to finish. Currently it is not possible to get the results from RallySimFans.hu while the rally is still in progress."
    case RefreshNotSupported() => "Press Auto 2023/2024 rally results are static and refreshing is not supported."
