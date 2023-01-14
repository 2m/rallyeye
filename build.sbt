scalaVersion := "3.2.1"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir"   %% "tapir-core"         % "1.2.4",
  "com.softwaremill.sttp.tapir"   %% "tapir-netty-server" % "1.2.4",
  "com.softwaremill.sttp.client3" %% "core"               % "3.8.5"
)

assembly / assemblyJarName := "rallyeye-data.jar"
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _                        => MergeStrategy.first
}
