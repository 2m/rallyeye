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

import scala.deriving.Mirror

import com.raquo.laminar.api.*
import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.Binder
import com.raquo.waypoint.*
import components.ResultFilter
import io.bullet.borer.Codec
import io.bullet.borer.Json
import io.bullet.borer.derivation.MapBasedCodecs.*
import org.scalajs.dom

object Router:
  sealed trait Page
  case object IndexPage extends Page
  case class RallyPage(rallyId: Int, results: String) extends Page
  case class PressAuto(year: Int, results: String) extends Page

  given Codec[Page] = deriveAllCodecs[Page]

  val indexRoute = Route.static(IndexPage, root / endOfSegments)

  val rsfRoute = Route[RallyPage, (Int, String)](
    encode = Tuple.fromProductTyped,
    decode = summon[Mirror.Of[RallyPage]].fromProduct,
    pattern = root / "rsf" / segment[Int] / segment[String] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val rsfRouteAllResults = Route[RallyPage, Int](
    encode = _.rallyId,
    decode = rallyId => RallyPage(rallyId, ResultFilter.AllResultsId),
    pattern = root / "rsf" / segment[Int] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val rallyRoute = Route[RallyPage, (Int, String)](
    Tuple.fromProductTyped,
    summon[Mirror.Of[RallyPage]].fromProduct,
    root / "rally" / segment[Int] / segment[String] / endOfSegments,
    Route.fragmentBasePath
  )

  val rallyRouteAllResults = Route[RallyPage, Int](
    _.rallyId,
    rallyId => RallyPage(rallyId, ResultFilter.AllResultsId),
    root / "rally" / segment[Int] / endOfSegments,
    Route.fragmentBasePath
  )

  val pressAutoRoute = Route[PressAuto, (Int, String)](
    Tuple.fromProductTyped,
    summon[Mirror.Of[PressAuto]].fromProduct,
    root / "pressauto" / segment[Int] / segment[String] / endOfSegments,
    Route.fragmentBasePath
  )

  val pressAutoRouteAllResults = Route[PressAuto, Int](
    _.year,
    year => PressAuto(year, ResultFilter.AllResultsId),
    root / "pressauto" / segment[Int] / endOfSegments,
    Route.fragmentBasePath
  )

  val router = new Router[Page](
    routes = List(
      rsfRoute,
      rsfRouteAllResults,
      rallyRoute,
      rallyRouteAllResults,
      pressAutoRoute,
      pressAutoRouteAllResults,
      indexRoute
    ),
    getPageTitle = _ => "RallyEye",
    serializePage = page => Json.encode(page).toUtf8String,
    deserializePage = pageStr => Json.decode(pageStr.getBytes("UTF8")).to[Page].value
  )(
    popStateEvents = L.windowEvents(_.onPopState),
    owner = L.unsafeWindowOwner
  )

  def navigateTo(page: Page): Binder[HtmlElement] = Binder { el =>

    val isLinkElement = el.ref.isInstanceOf[dom.html.Anchor]

    if isLinkElement then el.amend(href(router.absoluteUrlForPage(page)))

    // If element is a link and user is holding a modifier while clicking:
    //  - Do nothing, browser will open the URL in new tab / window / etc. depending on the modifier key
    // Otherwise:
    //  - Perform regular pushState transition
    (onClick
      .filter(ev => !(isLinkElement && (ev.ctrlKey || ev.metaKey || ev.shiftKey || ev.altKey)))
      .preventDefault
      --> (_ => router.pushState(page))).bind(el)
  }

  def withFilter(filter: String) = router.currentPageSignal.now() match
    case p: RallyPage => p.copy(results = filter)
    case p: PressAuto => p.copy(results = filter)
    case p            => p
