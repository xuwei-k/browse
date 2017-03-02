import sbt._
import Keys._
import Configurations.CompilerPlugin

object XRay extends Build {
  lazy val testAll = taskKey[Unit]("Runs all tests for this project.")

  lazy val main = Project("sxr", file(".")) settings(
                               name :=  "sxr",
          organization in ThisBuild :=  "org.improving",
               version in ThisBuild :=  "1.0.2",
          scalaVersion in ThisBuild :=  "2.11.8",
    crossScalaVersions in ThisBuild :=  List("2.12.1", "2.11.8", "2.10.6"),
                           licenses :=  Seq("BSD New" -> file("LICENSE").toURL),
                  ivyConfigurations +=  js,
                         exportJars :=  true,
                libraryDependencies ++= dependencies,
                libraryDependencies +=  "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
                          jqueryAll :=  target.value / "jquery-all.js",
                          combineJs :=  combineJquery(update.value, jqueryAll.value, streams.value.log),
      resourceGenerators in Compile <+= combineJs,
                           commands +=  Command.command("testAll")("test/test" :: "testLink/test" :: _)
  ) settings (crossSourceSettings: _*)

  lazy val test: Project = project.dependsOn(main % CompilerPlugin).settings(testProjectSettings: _*)

  lazy val testLink: Project = project.dependsOn(main % CompilerPlugin, test).settings(testProjectSettings: _*).settings(
    scalacOptions += {
      val _ = clean.value
      val linkFile = target.value / "links"
      val testLinkFile = classDirectory.in(test, Compile).value.getParentFile / "classes.sxr"
      IO.write(linkFile, testLinkFile.toURI.toURL.toExternalForm)
      s"-P:sxr:link-file:$linkFile"
    }
  )

  def crossSourceSettings = Seq(
       unmanagedSourceDirectories in Compile ++=
        Seq(
           (sourceDirectory in Compile).value / s"scala-${scalaBinaryVersion.value}",
           (sourceDirectory in Compile).value / s"scala-${scalaVersion.value}"
        )

  )

  def testProjectSettings = Seq(
    libraryDependencies ++= (scalaBinaryVersion.value match {
      case "2.10" => Seq()
      case _ =>      Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.6")
    }),
    autoCompilerPlugins :=  true,
     compile in Compile := (compile in Compile).dependsOn(clean).value,
              Keys.test :=  {
      val _ = (compile in Compile).value
      val out = (classDirectory in Compile).value
      val base = baseDirectory.value
      checkOutput(out / "../classes.sxr", base / "expected", streams.value.log)
    }
  )

  val js        = config("js").hide
  val combineJs = TaskKey[Seq[File]]("combine-js")
  val jqueryAll = SettingKey[File]("jquery-all")

  val jquery_version          = "1.11.1"
  val jquery_scrollto_version = "1.4.14"
  val jquery_qtip_version     = "2.1.1"

  def dependencies = Seq(
    "org.webjars" % "jquery"          % jquery_version          % "js->default",
    "org.webjars" % "jquery.scrollTo" % jquery_scrollto_version % "js->default",
    "org.webjars" % "qtip2"           % jquery_qtip_version     % "js->default"
  )

  def combineJquery(report: UpdateReport, jsOut: File, log: Logger): Seq[File] = {
    IO.delete(jsOut)
    inputs(report) foreach (in => appendJs(in, jsOut))
    log.info("Wrote combined js to " + jsOut.getAbsolutePath)
    Seq(jsOut)
  }
  def inputs(report: UpdateReport) =
    report select configurationFilter(js.name) sortBy (_.name)

  def appendJs(js: File, to: File): Unit =
    Using.fileInputStream(js)(in => Using.fileOutputStream(append = true)(to)(out => IO.transfer(in, out)))

  def isUpdate = sys.props contains "sxr.update"

  def checkOutput(sxrDir: File, expectedDir: File, log: Logger) {
    val actual = filesToCompare(sxrDir)
    val expected = filesToCompare(expectedDir)
    val actualRelative = actual._2s
    val expectedRelative = expected._2s
    if(actualRelative != expectedRelative) {
      val actualOnly = actualRelative -- expectedRelative
      val expectedOnly = expectedRelative -- actualRelative
      def print(n: Iterable[String]): String = n.mkString("\n\t", "\n\t", "\n")
      log.error(s"Actual filenames not expected: ${print(actualOnly)}Expected filenames not present: ${print(expectedOnly)}")
      error("Actual filenames differed from expected filenames.")
    }
    import collection.JavaConverters._
    val different = actualRelative filterNot { relativePath =>
      val actualFile = actual.reverse(relativePath).head
      val expectedFile = expected.reverse(relativePath).head
      val deltas = filteredDifferences(actualFile, expectedFile)
      if(!deltas.isEmpty) {
        if (isUpdate) {
          val cmd = s"cp $actualFile $expectedFile"
          log.info(s"Running: $cmd")
          scala.sys.process.Process(cmd).run()
        }
        else {
          // TODO - Display diffs.
          val diffDisplay =
            deltas.map(x => s"${prettyDelta(x)}").mkString("\n")
          log.error(s"$relativePath\n\t$actualFile\n\t$expectedFile\n$diffDisplay")
        }
      }
      deltas.isEmpty || isUpdate
    }
    if(different.nonEmpty)
      error("Actual content differed from expected content")
  }
  def prettyDelta(d: difflib.Delta): String = {
    import difflib.Delta
    import collection.JavaConverters._
    d.getType.name match {
      case "DELETE" => s"- ${d.getOriginal.getLines}"
      case "INSERT" => s"+ ${d.getRevised.getLines}"
      case "CHANGE" =>  // TODO - better diff here...
        (for {
          (lhs, rhs) <- d.getOriginal.getLines.asScala zip d.getRevised.getLines.asScala
        } yield s"< $lhs\n> $rhs").mkString("\n\n")
    }
  }
  def filesToCompare(dir: File): Relation[File,String] = {
    val mappings = dir ** ("*.html" | "*.index") x relativeTo(dir)
    Relation.empty ++ mappings
  }
  // TODO - Real diff util here for better error message.
  def filteredDifferences(actualFile: File, expectedFile: File): Seq[difflib.Delta] = {
    import collection.JavaConverters._
    for {
      diff <- lineDiff(actualFile, expectedFile).getDeltas.asScala
      if !isFileLocationDiff(diff)
    } yield diff
  }

  def isFileLocationDiff(diff: difflib.Delta): Boolean = {
    import collection.JavaConverters._
    // Here we try to ignore file location differences between example HTML and result.
    val replaceAllString = "\"file:[^\"]+\""
    diff.getOriginal.getLines.asScala zip diff.getRevised.getLines.asScala forall {
      case (lhs, rhs) =>
        (lhs.toString contains "file:") && {
          lhs.toString.replaceAll(replaceAllString, "") == rhs.toString.replaceAll(replaceAllString, "")
        }
    }
  }

  def lineDiff(actualFile: File, expectedFile: File): difflib.Patch =
     rawLineDiff(IO.readLines(actualFile), IO.readLines(expectedFile))

  def rawLineDiff(lines: List[String], expected: List[String]): difflib.Patch = {
    import difflib.DiffUtils
    import collection.JavaConverters._
    DiffUtils.diff(lines.asJava, expected.asJava)
  }
}
