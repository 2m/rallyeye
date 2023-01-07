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

import com.raquo.laminar.api._
import com.raquo.waypoint._
import upickle.default._

object Router:
  sealed trait Page
  case object IndexPage extends Page
  case class RallyPage(rallyId: Int) extends Page

  implicit val RallyPageRW: ReadWriter[RallyPage] = macroRW
  implicit val rw: ReadWriter[Page] = macroRW

  val indexRoute = Route.static(IndexPage, root / endOfSegments)

  val rallyRoute = Route[RallyPage, Int](
    encode = rallyPage => rallyPage.rallyId,
    decode = arg => RallyPage(rallyId = arg),
    pattern = root / "rally" / segment[Int] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val router = new Router[Page](
    routes = List(rallyRoute, indexRoute),
    getPageTitle = _ => "RallyEye",
    serializePage = page => write(page),
    deserializePage = pageStr => read(pageStr)(rw)
  )(
    popStateEvents = L.windowEvents(_.onPopState),
    owner = L.unsafeWindowOwner
  )
