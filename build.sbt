scalaVersion := "3.2.1"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir"   %% "tapir-core"         % "1.2.5",
  "com.softwaremill.sttp.tapir"   %% "tapir-netty-server" % "1.2.5",
  "com.softwaremill.sttp.client3" %% "core"               % "3.8.8"
)

assembly / assemblyJarName := "rallyeye-data.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}

enablePlugins(AutomateHeaderPlugin)
organizationName := "github.com/2m/rallyeye-data/contributors"
startYear := Some(2023)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
