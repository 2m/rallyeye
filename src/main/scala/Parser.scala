package rallyeye

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try
import scala.util.chaining._

import sttp.client3._

case class Entry(
    stageNumber: Int,
    stageName: String,
    userName: String,
    stageTime: Option[BigDecimal],
    overallTime: Option[BigDecimal],
    superRally: Boolean,
    finished: Boolean
)
case class TimeResult(userName: String, stageTime: BigDecimal, overallTime: BigDecimal, finished: Boolean)
case class PositionResult(userName: String, stagePosition: Int, overallPosition: Int)

def parse(csv: String) =
  val (header :: data) = csv.split('\n').toList: @unchecked
  val parsed = data.map(_.split(";", -1).toList).map {
    case stageNumber :: stageName :: _ :: userName :: _ :: _ :: _ :: _ :: _ :: time3 :: _ :: _ :: _ :: superRally :: finished :: _ :: Nil =>
      Entry(
        stageNumber.toInt,
        stageName,
        userName,
        Try(BigDecimal(time3)).toOption,
        None,
        superRally == "1",
        finished == "F"
      )
    case _ => ???
  }
  val withOverall = parsed
    .groupBy(_.userName)
    .view
    .mapValues { results =>
      val overallTimes =
        results.scanLeft(BigDecimal(0))((sofar, entry) => sofar + entry.stageTime.getOrElse(BigDecimal(0)))
      results.zip(overallTimes.drop(1)).map((e, overall) => e.copy(overallTime = Some(overall)))
    }
    .values
    .flatten

  val grouped = withOverall
    .groupBy(entry => Stage(entry.stageName))
    .view
    .mapValues(v =>
      v.collect { case Entry(_, _, userName, Some(stageTime), Some(overallTime), _, finished) =>
        TimeResult(userName, stageTime, overallTime, finished)
      }
    )

  val positions = grouped.mapValues { results =>
    val stageResults = results.toList.filter(_.finished).sortBy(_.stageTime)
    val overallResults = results.toList.filter(_.finished).sortBy(_.overallTime)
    overallResults.zipWithIndex.map { (result, overall) =>
      PositionResult(result.userName, stageResults.indexOf(result) + 1, overall + 1)
    }
  }

  positions.mapValues(results =>
    results.groupBy(_.userName).toList.map { (driver, results) =>
      Driver(driver, results.map(r => Result(r.stagePosition, r.overallPosition)))
    }
  )

def fetch() =
  val backend = FetchBackend()
  val response = basicRequest
    .get(uri"results.csv")
    .send(backend)

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  response.map(_.body.getOrElse("") pipe parse)
