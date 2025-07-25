ThisBuild / scalaVersion := "3.7.1"
ThisBuild / scalafmtOnCompile := true

ThisBuild / organization := "lt.dvim.rallyeye"
ThisBuild / organizationName := "github.com/2m/rallyeye/contributors"
ThisBuild / startYear := Some(2022)
ThisBuild / licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))

ThisBuild / dynverSeparator := "-"

ThisBuild / libraryDependencies += compilerPlugin("com.github.ghik" % "zerowaste" % "1.0.0" cross CrossVersion.full)

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
      "com.softwaremill.sttp.tapir" %%% "tapir-core"       % "1.11.40",
      "io.bullet"                   %%% "borer-derivation" % "1.16.1",
      "io.github.iltotore"          %%% "iron"             % "3.0.2",
      "io.github.iltotore"          %%% "iron-borer"       % "3.0.2"
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
      "org.scala-js"                %%% "scalajs-dom"                 % "2.8.1",
      "org.scala-js"                %%% "scala-js-macrotask-executor" % "1.1.1",
      "com.softwaremill.sttp.tapir" %%% "tapir-sttp-client"           % "1.11.40",
      "com.raquo"                   %%% "laminar"                     % "17.2.1",
      "com.raquo"                   %%% "waypoint"                    % "9.0.0",
      "io.github.cquiroz"           %%% "scala-java-time"             % "2.6.0",
      "io.bullet"                   %%% "borer-core"                  % "1.16.1",
      "io.bullet"                   %%% "borer-derivation"            % "1.16.1",
      "com.lihaoyi"                 %%% "utest"                       % "0.8.9" % Test
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
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"                       % "1.11.40",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-client"                       % "1.11.40",
      "org.http4s"                  %% "http4s-ember-server"                       % "0.23.30",
      "org.http4s"                  %% "http4s-ember-client"                       % "0.23.30",
      "org.http4s"                  %% "http4s-otel4s-middleware-trace-client"     % "0.13.0",
      "org.http4s"                  %% "http4s-otel4s-middleware-trace-server"     % "0.13.0",
      "io.bullet"                   %% "borer-compat-circe"                        % "1.16.1",
      "ch.qos.logback"               % "logback-classic"                           % "1.5.18",
      "com.github.geirolz"          %% "fly4s-core"                                % "1.0.0",
      "org.xerial"                   % "sqlite-jdbc"                               % "3.50.3.0",
      "org.tpolecat"                %% "doobie-core"                               % "1.0.0-RC9",
      "io.github.arainko"           %% "ducktape"                                  % "0.2.9",
      "com.monovore"                %% "decline-effect"                            % "2.5.0",
      "io.github.iltotore"          %% "iron"                                      % "3.0.2",
      "io.github.iltotore"          %% "iron-doobie"                               % "3.0.2",
      "com.themillhousegroup"       %% "scoup"                                     % "1.0.0",
      "org.gnieh"                   %% "fs2-data-csv"                              % "1.12.0",
      "org.typelevel"               %% "log4cats-core"                             % "2.7.1",
      "org.typelevel"               %% "log4cats-slf4j"                            % "2.7.1",
      "io.github.arturaz"           %% "otel4s-doobie"                             % "0.7.1",
      "org.typelevel"               %% "otel4s-oteljava"                           % "0.13.1",
      "org.typelevel"              %%% "otel4s-instrumentation-metrics"            % "0.13.1",
      "io.opentelemetry"             % "opentelemetry-exporter-otlp"               % "1.51.0",
      "io.opentelemetry"             % "opentelemetry-sdk-extension-autoconfigure" % "1.51.0",
      "org.tpolecat"                %% "doobie-munit"                              % "1.0.0-RC9" % Test,
      "org.scalameta"               %% "munit"                                     % "1.1.1"     % Test,
      "org.typelevel"               %% "munit-cats-effect"                         % "2.1.0"     % Test,
      "org.typelevel"               %% "scalacheck-effect-munit"                   % "2.0.0-M2"  % Test,
      "org.scalameta"               %% "munit-scalacheck"                          % "1.1.0"     % Test,
      "com.github.jatcwang"         %% "difflicious-munit"                         % "0.4.3"     % Test,
      "io.github.iltotore"          %% "iron-scalacheck"                           % "3.0.2"     % Test,
      "com.rallyhealth"             %% "scalacheck-ops_1"                          % "2.12.0"    % Test
    ),
    libraryDependencySchemes ++= Seq(
      // while http4s-otel4s-middleware depends on older otel4s version
      "org.typelevel" %% "otel4s-core-trace"  % VersionScheme.Always,
      "org.typelevel" %% "otel4s-core-common" % VersionScheme.Always
    ),

    // for correct IOApp resource cleanup
    Compile / run / fork := true,

    // disable varnings for given search alternatives
    scalacOptions ++= Seq("-source", "3.7"),

    // exclude integration tests
    Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=integration"),
    Integration / testOptions := Seq(Tests.Argument(MUnitFramework, "--include-tags=integration")),

    // include some build settings into module code
    buildInfoKeys := Seq[BuildInfoKey](
      isSnapshot, // for determining which backend URL to use
      Compile / resourceDirectory, // for press auto results loader
      Test / resourceDirectory // for integration test snapshots
    ),
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
      s"-Dorg.sqlite.lib.exportPath=${nativeImageOutput.value.getParent}", // https://github.com/xerial/sqlite-jdbc#graalvm-native-image-support
      "--enable-url-protocols=https", // for OpenTelemetry export to honeycomb
      "-H:+AddAllCharsets" // include all charsets for RSF page parsing
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
