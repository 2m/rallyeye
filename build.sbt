// heavily inspired by https://github.com/sjrd/scalajs-sbt-vite-laminar-chartjs-example/blob/laminar-scalablytyped-end-state/build.sbt

scalaVersion := "3.2.2"
scalacOptions ++= Seq("-encoding", "utf-8", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.scala-js"                  %%% "scalajs-dom"     % "2.4.0",
  "com.softwaremill.sttp.client3" %%% "core"            % "3.8.11",
  "com.raquo"                     %%% "laminar"         % "15.0.0-M6",
  "com.raquo"                     %%% "waypoint"        % "6.0.0-M4",
  "io.github.cquiroz"             %%% "scala-java-time" % "2.5.0",
  "com.lihaoyi"                   %%% "upickle"         % "3.0.0-M2",
  "com.lihaoyi"                   %%% "utest"           % "0.8.1" % "test"
)

testFrameworks += new TestFramework("utest.runner.Framework")

scalafmtOnCompile := true

// scalajs
import org.scalajs.linker.interface.ModuleSplitStyle
enablePlugins(ScalaJSPlugin)

// Tell Scala.js that this is an application with a main method
scalaJSUseMainModuleInitializer := true

/* Configure Scala.js to emit modules in the optimal way to
 * connect to Vite's incremental reload.
 * - emit ECMAScript modules
 * - emit as many small modules as possible for classes in the "rallyeye" package
 * - emit as few (large) modules as possible for all other classes
 *   (in particular, for the standard library)
 */
scalaJSLinkerConfig ~= {
  _.withModuleKind(ModuleKind.ESModule)
    .withModuleSplitStyle(
      ModuleSplitStyle.SmallModulesFor(List("rallyeye"))
    )
}

def linkerOutputDirectory(v: Attributed[org.scalajs.linker.interface.Report], t: File): Unit = {
  val output = v.get(scalaJSLinkerOutputDirectory.key).getOrElse {
    throw new MessageOnlyException(
      "Linking report was not attributed with output directory. " +
        "Please report this as a Scala.js bug."
    )
  }
  IO.write(t / "linker-output.txt", output.getAbsolutePath.toString)
  ()
}

val publicDev = taskKey[Unit]("output directory for `npm run dev`")
val publicProd = taskKey[Unit]("output directory for `npm run build`")

publicDev := linkerOutputDirectory((Compile / fastLinkJS).value, target.value)
publicProd := linkerOutputDirectory((Compile / fullLinkJS).value, target.value)

// scalably typed
enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
externalNpm := baseDirectory.value // Tell ScalablyTyped that we manage `npm install` ourselves

enablePlugins(AutomateHeaderPlugin)
organizationName := "github.com/2m/rallyeye/contributors"
startYear := Some(2022)
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
