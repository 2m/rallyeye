scalaVersion := "3.2.2"

libraryDependencies ++= Seq(
  ("com.softwaremill.sttp.tapir"   %% "tapir-akka-http-server" % "1.2.10").cross(CrossVersion.for3Use2_13),
  ("com.softwaremill.sttp.client3" %% "akka-http-backend"      % "3.8.13").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-stream"            % "2.8.0").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-actor-typed"       % "2.8.0").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-http"              % "10.5.0").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-http-caching"      % "10.5.0").cross(CrossVersion.for3Use2_13),
  ("ch.megard"                     %% "akka-http-cors"         % "1.2.0").cross(CrossVersion.for3Use2_13),
  "ch.qos.logback"                  % "logback-classic"        % "1.4.7"
)

enablePlugins(AutomateHeaderPlugin)
organizationName := "github.com/2m/rallyeye-data/contributors"
startYear := Some(2023)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

jibOrganization := "martynas"
jibTags += "latest"
ThisBuild / dynverSeparator := "-"
