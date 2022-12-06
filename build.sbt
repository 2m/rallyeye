// heavily inspired by https://github.com/sjrd/scalajs-sbt-vite-laminar-chartjs-example/blob/laminar-scalablytyped-end-state/build.sbt

scalaVersion := "3.2.1"
scalacOptions ++= Seq("-encoding", "utf-8", "-deprecation", "-feature")

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-dom" % "2.2.0",
  "com.lihaoyi" %%% "utest"        % "0.8.1" % "test"
)

testFrameworks += new TestFramework("utest.runner.Framework")

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

def linkerOutputDirectory(v: Attributed[org.scalajs.linker.interface.Report]): File =
  v.get(scalaJSLinkerOutputDirectory.key).getOrElse {
    throw new MessageOnlyException(
      "Linking report was not attributed with output directory. " +
        "Please report this as a Scala.js bug."
    )
  }

val publicDev = taskKey[String]("output directory for `npm run dev`")
val publicProd = taskKey[String]("output directory for `npm run build`")

publicDev := linkerOutputDirectory((Compile / fastLinkJS).value).getAbsolutePath()
publicProd := linkerOutputDirectory((Compile / fullLinkJS).value).getAbsolutePath()

// scalably typed
enablePlugins(ScalablyTypedConverterExternalNpmPlugin)
externalNpm := baseDirectory.value // Tell ScalablyTyped that we manage `npm install` ourselves
