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
import components.RallyList
import components.ResultFilter
import io.bullet.borer.Codec
import io.bullet.borer.Json
import io.bullet.borer.derivation.MapBasedCodecs.*
import org.scalajs.dom
import rallyeye.shared.Codecs.given
import rallyeye.shared.RallyKind
import urldsl.errors.DummyError
import urldsl.vocabulary.FromString
import urldsl.vocabulary.Printer

object Router:
  sealed trait Page
  case object IndexPage extends Page
  case object AboutPage extends Page
  case class RallyPage(rallyId: String, results: String) extends Page
  case class PressAuto(year: String, results: String) extends Page
  case class Ewrc(rallyId: String, results: String) extends Page
  case class FindPage(filter: RallyList.Championship) extends Page
  case object FreshRallyPage extends Page

  given Codec[RallyList.Championship] = deriveCodec[RallyList.Championship]
  given Codec[RallyList.Fresh.type] = deriveCodec[RallyList.Fresh.type]
  given Codec[RallyList.Filter] = deriveCodec[RallyList.Filter]
  given Codec[Page] = deriveAllCodecs[Page]
  given FromString[RallyKind, DummyError] = new FromString[RallyKind, DummyError]:
    def fromString(kind: String) =
      RallyKind.values.find(_.toString.toLowerCase == kind).toRight(DummyError.dummyError)
  given Printer[RallyKind] = new Printer[RallyKind]:
    def print(kind: RallyKind) = kind.toString.toLowerCase

  val indexRoute = Route.static(IndexPage, root / endOfSegments)
  val aboutRoute = Route.static(AboutPage, root / "about" / endOfSegments, Route.fragmentBasePath)

  val rsfRoute = Route[RallyPage, (String, String)](
    encode = Tuple.fromProductTyped,
    decode = summon[Mirror.Of[RallyPage]].fromProduct,
    pattern = root / "rsf" / segment[String] / segment[String] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val rsfRouteAllResults = Route[RallyPage, String](
    encode = _.rallyId,
    decode = rallyId => RallyPage(rallyId, ResultFilter.AllResultsId),
    pattern = root / "rsf" / segment[String] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val pressAutoRoute = Route[PressAuto, (String, String)](
    Tuple.fromProductTyped,
    summon[Mirror.Of[PressAuto]].fromProduct,
    root / "pressauto" / segment[String] / segment[String] / endOfSegments,
    Route.fragmentBasePath
  )

  val pressAutoRouteAllResults = Route[PressAuto, String](
    _.year,
    year => PressAuto(year, ResultFilter.AllResultsId),
    root / "pressauto" / segment[String] / endOfSegments,
    Route.fragmentBasePath
  )

  val ewrcRoute = Route[Ewrc, (String, String)](
    encode = Tuple.fromProductTyped,
    decode = summon[Mirror.Of[Ewrc]].fromProduct,
    pattern = root / "ewrc" / segment[String] / segment[String] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val ewrcRouteAllResults = Route[Ewrc, String](
    encode = _.rallyId,
    decode = rallyId => Ewrc(rallyId, ResultFilter.AllResultsId),
    pattern = root / "ewrc" / segment[String] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val findRoute = Route[FindPage, (RallyKind, String, Int)](
    encode = f => (f.filter.kind, f.filter.championship, f.filter.year.getOrElse(0)),
    decode = (kind, championship, year) =>
      FindPage(RallyList.Championship(kind, championship, if year == 0 then None else Some(year))),
    pattern = root / "find" / segment[RallyKind] / segment[String] / segment[Int] / endOfSegments,
    basePath = Route.fragmentBasePath
  )

  val freshRallyRoute = Route.static(FreshRallyPage, root / "fresh" / endOfSegments, Route.fragmentBasePath)

  val router = new Router[Page](
    routes = List(
      rsfRoute,
      rsfRouteAllResults,
      pressAutoRoute,
      pressAutoRouteAllResults,
      ewrcRoute,
      ewrcRouteAllResults,
      aboutRoute,
      findRoute,
      freshRallyRoute,
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
    case p: RallyPage           => p.copy(results = filter)
    case p: PressAuto           => p.copy(results = filter)
    case p: Ewrc                => p.copy(results = filter)
    case p: IndexPage.type      => p
    case p: AboutPage.type      => p
    case p: FindPage            => p
    case p: FreshRallyPage.type => p
