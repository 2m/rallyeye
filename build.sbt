scalaVersion := "3.2.1"

libraryDependencies ++= Seq(
  ("com.softwaremill.sttp.tapir"   %% "tapir-akka-http-server" % "1.2.9").cross(CrossVersion.for3Use2_13),
  ("com.softwaremill.sttp.client3" %% "akka-http-backend"      % "3.8.11").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-stream"            % "2.8.0-M5").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-actor-typed"       % "2.8.0-M5").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-http"              % "10.5.0").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka"             %% "akka-http-caching"      % "10.5.0").cross(CrossVersion.for3Use2_13),
  ("ch.megard"                     %% "akka-http-cors"         % "1.2.0").cross(CrossVersion.for3Use2_13),
  "ch.qos.logback"                  % "logback-classic"        % "1.2.11"
)

assembly / assemblyJarName := "rallyeye-data.jar"

enablePlugins(AutomateHeaderPlugin)
organizationName := "github.com/2m/rallyeye-data/contributors"
startYear := Some(2023)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
