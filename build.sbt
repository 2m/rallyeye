scalaVersion := "3.2.1"

Compile / npmDependencies ++= Seq(
  "d3-array" -> "3.2.0",
  "@types/d3-array" -> "3.0.3",
  "d3-selection" -> "3.0.0",
  "@types/d3-selection" -> "3.0.2",
  "d3-scale" -> "4.0.2",
  "@types/d3-scale" -> "4.0.2"
)

stFlavour := Flavour.Slinky
useYarn := true

scalaJSUseMainModuleInitializer := true

// bundler settings
webpackCliVersion := "4.10.0"
Compile / fastOptJS / webpackExtraArgs += "--mode=development"
Compile / fullOptJS / webpackExtraArgs += "--mode=production"
Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development"
Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production"

enablePlugins(ScalablyTypedConverterPlugin)

/** From https://github.com/ScalablyTyped/Demos/blob/master/build.sbt#L348
  */
lazy val dist = TaskKey[File]("dist")
dist := {
  import java.nio.file.Files
  import java.nio.file.StandardCopyOption.REPLACE_EXISTING

  val artifacts = (Compile / fullOptJS / webpack).value
  val artifactFolder = (Compile / fullOptJS / crossTarget).value
  val distFolder = (ThisBuild / baseDirectory).value / "dist"

  distFolder.mkdirs()
  artifacts.foreach { artifact =>
    val target = artifact.data.relativeTo(artifactFolder) match {
      case None          => distFolder / artifact.data.name
      case Some(relFile) => distFolder / relFile.toString
    }

    Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
  }

  val indexFrom = baseDirectory.value / "src/main/js/index.html"
  val indexTo = distFolder / "index.html"

  val indexPatchedContent = {
    import collection.JavaConverters._
    Files
      .readAllLines(indexFrom.toPath, IO.utf8)
      .asScala
      .map(_.replaceAllLiterally("-fastopt-", "-opt-"))
      .mkString("\n")
  }

  Files.write(indexTo.toPath, indexPatchedContent.getBytes(IO.utf8))
  distFolder
}
