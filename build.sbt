scalaVersion := "3.2.2"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.3.0",
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.3.0",
  "org.http4s"                  %% "http4s-ember-server" % "0.23.19",
  "org.http4s"                  %% "http4s-ember-client" % "0.23.19",
  "io.chrisdavenport"           %% "mules-http4s"        % "0.4.0",
  "io.chrisdavenport"           %% "mules-caffeine"      % "0.7.0",
  "io.bullet"                   %% "borer-derivation"    % "1.10.2",
  "org.scalameta"               %% "munit"               % "1.0.0-M7" % Test,
  "com.eed3si9n.expecty"        %% "expecty"             % "0.16.0"   % Test
)

scalacOptions ++= Seq("-Xmax-inlines", "64")

enablePlugins(AutomateHeaderPlugin)
organizationName := "github.com/2m/rallyeye-data/contributors"
startYear := Some(2023)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

jibOrganization := "martynas"
jibTags += "latest"
ThisBuild / dynverSeparator := "-"
