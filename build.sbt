ThisBuild / scalaVersion := "3.4.0"
//ThisBuild / scalacOptions ++= Seq("-Wunused:imports")
ThisBuild / scalafmtOnCompile := true

ThisBuild / organization := "lt.dvim.rallyeye"
ThisBuild / organizationName := "github.com/2m/rallyeye/contributors"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / dynverSeparator := "-"

// invisible because used from dyn task
Global / excludeLintKeys ++= Set(nativeImageJvm, nativeImageVersion)

val MUnitFramework = new TestFramework("munit.Framework")
val Integration = config("integration").extend(Test)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/shared"))
  .settings(
    name := "shared",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"       % "1.9.10",
      "io.bullet"                   %% "borer-derivation" % "1.14.0",
      "io.github.iltotore"          %% "iron"             % "2.4.0"
    ),
    // for borer semi-automatic derivation
    scalacOptions ++= Seq("-Xmax-inlines", "64")
  )
  .enablePlugins(AutomateHeaderPlugin)

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

lazy val frontend = project
  .in(file("modules/frontend"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-js"                %%% "scalajs-dom"                 % "2.8.0",
      "org.scala-js"                %%% "scala-js-macrotask-executor" % "1.1.1",
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client"           % "1.9.10",
      "com.raquo"                   %%% "laminar"                     % "16.0.0",
      "com.raquo"                   %%% "waypoint"                    % "7.0.0",
      "io.github.cquiroz"           %%% "scala-java-time"             % "2.5.0",
      "io.bullet"                   %%% "borer-core"                  % "1.14.0",
      "io.bullet"                   %%% "borer-derivation"            % "1.14.0",
      "com.lihaoyi"                 %%% "utest"                       % "0.8.2" % "test"
    ),
    // Tell Scala.js that this is an application with a main method
    scalaJSUseMainModuleInitializer := true,

    /* Configure Scala.js to emit modules in the optimal way to
     * connect to Vite's incremental reload.
     * - emit ECMAScript modules
     * - emit as many small modules as possible for classes in the "rallyeye" package
     * - emit as few (large) modules as possible for all other classes
     *   (in particular, for the standard library)
     */
    scalaJSLinkerConfig ~= {
      import org.scalajs.linker.interface.ModuleSplitStyle
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("rallyeye"))
        )
    },
    publicDev := linkerOutputDirectory((Compile / fastLinkJS).value, target.value),
    publicProd := linkerOutputDirectory((Compile / fullLinkJS).value, target.value),

    // scalably typed
    externalNpm := baseDirectory.value, // Tell ScalablyTyped that we manage `npm install` ourselves

    // build info
    buildInfoKeys := Seq[BuildInfoKey](version, isSnapshot),
    buildInfoPackage := "rallyeye"
  )
  .dependsOn(shared.js)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ScalaJSPlugin)
  .enablePlugins(
    (if (!sys.env.get("SCALA_STEWARD").isDefined) Seq(ScalablyTypedConverterExternalNpmPlugin) else Seq.empty)*
  )
  .enablePlugins(BuildInfoPlugin)

lazy val backend = project
  .in(file("modules/backend"))
  .configs(Integration)
  .settings(
    inConfig(Integration)(Defaults.testTasks),
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.9.10",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client" % "1.9.10",
      "org.http4s"                  %% "http4s-ember-server" % "0.23.25",
      "org.http4s"                  %% "http4s-ember-client" % "0.23.25",
      "ch.qos.logback"               % "logback-classic"     % "1.5.2",
      "com.github.geirolz"          %% "fly4s-core"          % "1.0.0",
      "org.flywaydb"                 % "flyway-core"         % "10.9.1", // fixes logging
      "org.xerial"                   % "sqlite-jdbc"         % "3.45.1.0",
      "org.tpolecat"                %% "doobie-core"         % "1.0.0-RC4",
      "io.github.arainko"           %% "ducktape"            % "0.1.11",
      "com.monovore"                %% "decline-effect"      % "2.4.1",
      "io.github.iltotore"          %% "iron"                % "2.4.0",
      "io.github.iltotore"          %% "iron-doobie"         % "2.4.0",
      "com.themillhousegroup"       %% "scoup"               % "1.0.0",
      "org.typelevel"               %% "log4cats-core"       % "2.6.0",
      "org.typelevel"               %% "log4cats-slf4j"      % "2.6.0",
      "org.tpolecat"                %% "doobie-munit"        % "1.0.0-RC4" % Test,
      "org.scalameta"               %% "munit"               % "1.0.0-M11" % Test,
      "org.scalameta"               %% "munit-scalacheck"    % "1.0.0-M11" % Test,
      "com.softwaremill.diffx"      %% "diffx-munit"         % "0.9.0"     % Test,
      "io.github.iltotore"          %% "iron-scalacheck"     % "2.4.0"     % Test,
      "com.rallyhealth"             %% "scalacheck-ops_1"    % "2.12.0"    % Test
    ),

    // for correct IOApp resource cleanup
    Compile / run / fork := true,

    // for diffx assertions in tests
    Test / scalacOptions ++= Seq("-Xmax-inlines", "64"),

    // exclude integration tests
    Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=integration"),
    Integration / testOptions := Seq(Tests.Argument(MUnitFramework, "--include-tags=integration")),

    // for integration test snapshots
    buildInfoKeys := Seq[BuildInfoKey](Test / resourceDirectory),
    buildInfoPackage := "rallyeye",

    // native image
    nativeImageJvm := "graalvm-java21",
    nativeImageVersion := "21.0.2",
    nativeImageAgentOutputDir := (Compile / resourceDirectory).value / "META-INF" / "native-image" / organization.value / name.value,
    nativeImageOptions ++= List(
      "--verbose",
      "--no-fallback", // show the underlying problem due to unsupported features instead of building a fallback image
      "-H:IncludeResources=db/V.*sql$",
      "-march=compatibility", // Use most compatible instructions, 'native' fails to start on flyio
      s"-Dorg.sqlite.lib.exportPath=${nativeImageOutput.value.getParent}" // https://github.com/xerial/sqlite-jdbc#graalvm-native-image-support
    ),

    // docker image build
    docker / dockerfile := {
      val artifactDir = nativeImage.value.getParentFile

      new Dockerfile {
        from("alpine:3.19")

        run("apk", "add", "gcompat") // GNU C Library compatibility layer for native image
        copy(artifactDir ** "*" filter { !_.isDirectory } get, "/app/")

        // lite fs setup
        run("apk", "add", "ca-certificates", "fuse3", "sqlite")
        customInstruction("COPY", "--from=flyio/litefs:0.5 /usr/local/bin/litefs /usr/local/bin/litefs")
        env("RALLYEYE_DB_PATH" -> "/litefs", "RALLYEYE_SERVER_PORT" -> "8081")
        add(baseDirectory.value / "litefs.yml", "/etc/litefs.yml")
        entryPoint("litefs", "mount")
      }
    },
    docker / imageNames := Seq(
      ImageName("registry.fly.io/rallyeye-data:latest"),
      ImageName(s"registry.fly.io/rallyeye-data:${version.value}")
    )
  )
  .dependsOn(shared.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(NativeImagePlugin)
  .enablePlugins(DockerPlugin)
