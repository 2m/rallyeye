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
package loader

import java.nio.file.Files
import java.util.UUID

import scala.annotation.nowarn
import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters.*
import scala.util.chaining.*

import cats.data.EitherT
import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.themillhousegroup.scoup.Scoup
import fs2.io.net.Network
import io.bullet.borer.Codec
import io.bullet.borer.compat.circe.*
import io.bullet.borer.derivation.MapBasedCodecs.*
import io.circe.Json
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.uri
import org.typelevel.otel4s.trace.Tracer
import rallyeye.shared.TapirJsonBorer.*
import sttp.tapir.*
import sttp.tapir.client.http4s.Http4sClientInterpreter
import sttp.tapir.generic.auto.*

// http https://raceadmin.eu/ajax/62eb5800-feda-492d-9580-171ef3ef6957/results/match/SS21/all/get
object PressAuto:
  val RaceAdmin = uri"https://raceadmin.eu"

  case class Rally(id: UUID, stages: Map[String, String])
  val pa2024 = Rally(
    UUID.fromString("62eb5800-feda-492d-9580-171ef3ef6957"),
    ListMap(
      "SS1" -> "Toyota I (Širvintos)",
      "SS2" -> "Toyota II (Širvintos)",
      "SS3a" -> "TV3 I (Molėtai)",
      "SS3b" -> "TV3 II (Molėtai)",
      "SS4" -> "Auto Bild I (Anykščiai)",
      "SS5" -> "Auto Bild II (Anykščiai)",
      "SS6" -> "Inbalance Grid I (Anykščiai)",
      "SS7" -> "Inbalance Grid II (Anykščiai)",
      "SS8" -> "Auresa I (Panevėžys)",
      "SS9" -> "Auresa II (Panevėžys)",
      "SS10" -> "Febi I (Kuršėnai)",
      "SS11" -> "Febi II (Kuršėnai)",
      "LK Day 1" -> "LK Day 1",
      "SS12" -> "Toyota C-HR I (Dubysos g.)",
      "SS13" -> "Toyota C-HR II (Dubysos g.)",
      "SS14" -> "Continental I (Mickai)",
      "SS15" -> "Continental II (Mickai)",
      "SS16" -> "Transeksta I (Švėpelių g.)",
      "SS17" -> "Deals On Wheels I (Taikos pr.)",
      "SS18" -> "Deals On Wheels II (Taikos pr.)",
      "SS19" -> "Continental III (Mickai)",
      "SS20" -> "Continental IV (Mickai)",
      "LK Day 2" -> "LK Day 2"
    )
  )

  case class RaceAdminResponse(data: List[RaceAdminEntry])
  case class RaceAdminEntry(
      position: Int | String,
      name: String,
      racerNumber: String,
      racerClass: String,
      teamNameOrSponsor: String,
      vehicle: String,
      totalSpentTime: String
  )

  given Codec[Int | String] =
    Codec.bimap[Json, Int | String](
      _ => ???,
      input =>
        input.asNumber match
          case Some(n) => n.toInt.get
          case None    => input.asString.get
    )
  given Schema[Int | String] = Schema.derivedUnion[Int | String]

  given Codec[RaceAdminEntry] = deriveCodec[RaceAdminEntry]
  given Codec[RaceAdminResponse] = deriveCodec[RaceAdminResponse]

  val data = endpoint
    .in("ajax" / path[UUID] / "results" / "match" / path[String] / "all" / "get")
    .out(jsonBody[RaceAdminResponse])

  def stageResultsJson[F[_]: Async](rally: UUID, stage: String) =
    Http4sClientInterpreter[F]().toRequestThrowDecodeFailures(data, Some(RaceAdmin))(rally, stage)

  def stageResults[F[_]: Async](client: Client[F], rally: UUID, stage: String) =
    val (request, parseResponse) = stageResultsJson(rally, stage)
    for response <- EitherT(
        client
          .run(request)
          .use(parseResponse(_))
          .map(_.left.map(_ => Error(s"Unable to parse $stage results response")))
      )
    yield response.data.map(StageEntry(stage, _))

  case class StageEntry(stage: String, entry: RaceAdminEntry)

  var vehicleCleanup = Map(
    "Toyota GR Yaris (2720)" -> List(
      "Toyota GR Yaris  (2720)",
      "Toyota GR Yaris   (2720)",
      "Toyota GR Yaris  (1600)",
      "Toyota GR Yaris   (1600)"
    ),
    "Mazda MX-5 (1998)" -> List(
      "Mazda MX-5  (1998)",
      "Mazda MX-5  (1999)"
    )
  )

  case class Racer(number: String, country: String, name: String, group: String, vehicle: String)
  object Racer:
    def apply(entry: RaceAdminEntry): Racer =
      val racerNumber = Scoup.parseHTML(entry.racerNumber).text
      val (country, name) =
        val names = Scoup.parseHTML(entry.name)
        names.select(".d-xl-none").remove: @nowarn("msg=discarded expression") // remove short names

        val drivers = names.select("div > span").iterator().asScala.toList
        val driverName = drivers.head.text
        val driverCountry = drivers.head.select("img").attr("title")
        val codriver = drivers.last.text
        driverCountry -> s"$driverName - $codriver"

      val vehicle = vehicleCleanup
        .collectFirst:
          case (vehicle, vehicleList) if vehicleList.contains(entry.vehicle) => vehicle
        .getOrElse(entry.vehicle)
        .replaceAll("\\s+", " ")

      Racer(racerNumber, country, name, entry.racerClass, vehicle)

  case class Time(spentTime: String)
  object Time:
    def apply(stageEntry: StageEntry): Time =
      val time =
        val times = Scoup.parseHTML(stageEntry.entry.totalSpentTime)
        times.select(".text-secondary").remove: @nowarn("msg=discarded expression") // remove diff time
        times.text

      val timeWithDefault = time match
        case t if t.isBlank && stageEntry.stage.contains("LK Day") => "00:00:00.000"
        case t                                                     => t

      val timeWithNominal = timeWithDefault match
        case t if stageEntry.stage.contains("LK Day") => s"$t (N)"
        case t                                        => t

      Time(timeWithNominal)

  def parseResults(results: List[StageEntry]) =
    val header = List("Country", "#", "Name", "Competitor", "Group", "Vehicle") ++ pa2024.stages.values.toList ++ List(
      "Total spent time"
    )
    val res = results
      .groupBy(_.entry pipe Racer.apply)
      .view
      .mapValues(_.groupMap(_.stage)(Time.apply).view.mapValues(_.head))
      .map: (racer, stages) =>
        val prefix = List(racer.country, racer.number, racer.name, "", racer.group, racer.vehicle)
        val stageTimes =
          pa2024.stages.keys.toList.map(stage => stages.get(stage).map(_.spentTime).getOrElse(""))
        prefix ++ stageTimes ++ List("")
    header :: res.toList

  def writeCsv(file: String, results: List[List[String]]) =
    val path = BuildInfo.resourceDirectory.toPath.resolve(file)
    Files.write(path, results.map(_.mkString(";")).mkString("\n").getBytes)

def loadPressAuto[F[_]: Async: Tracer: Network] =
  EmberClientBuilder
    .default[F]
    .withTimeout(Timeout)
    .withIdleConnectionTime(IdleTimeout)
    .build
    .map(Telemetry.tracedClient)
    .flatMap { client =>
      val res = (for
        results <- PressAuto.pa2024.stages.keys.toList
          .map(ss => PressAuto.stageResults(client, PressAuto.pa2024.id, ss))
          .sequence
          .map(_.flatten)
        parsedResults = PressAuto.parseResults(results)
        _ = PressAuto.writeCsv("pressauto2024.csv", parsedResults)
      yield ()).value.flatMap:
        case Right(_)    => ().pure[F]
        case Left(error) => println(s"error! $error"); Tracer[F].currentSpanOrNoop.flatMap(_.recordException(error))
      Resource.eval(res)
    }
    .rootSpan("load-press-auto")
