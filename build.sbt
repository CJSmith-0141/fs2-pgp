lazy val V = new {
  val SCALA_2_12 = "2.12.20"
  val SCALA_2_13 = "2.13.15"
  val SCALA_3 = "3.3.4"
  val Scalas = Seq(SCALA_3, SCALA_2_13)
  val ScalafixScalaVersions = Scalas.filterNot(_.startsWith("3"))
}

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / crossScalaVersions := V.Scalas
ThisBuild / organization := "net.tazato"
ThisBuild / homepage := Option(url("https://github.com/Dwolla/fs2-pgp"))
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))
ThisBuild / startYear := Option(2020)
ThisBuild / developers := List(
  Developer(
    "bpholt",
    "Brian Holt",
    "bholt@dwolla.com",
    url("https://dwolla.com")
  )
)
ThisBuild / githubWorkflowBuild := Seq(WorkflowStep.Sbt(List("test", "mimaReportBinaryIssues", "doc")))
ThisBuild / tlJdkRelease := Option(8)
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowScalaVersions := Seq("3", "2.13")
ThisBuild / tlCiReleaseBranches := Seq("main", "series/0.5")
ThisBuild / tlBaseVersion := "0.6"
ThisBuild / tlSonatypeUseLegacyHost := false
// ThisBuild / mergifyStewardConfig ~= {
//   _.map(_.copy(mergeMinors = true, author = "dwolla-oss-scala-steward[bot]"))
// }

lazy val `fs2-pgp-root` = project
  .in(file("."))
  .settings(publishArtifact := false)
  .enablePlugins(BouncyCastlePlugin)
  // .aggregate(`scalafix-rules`, `scalafix-tests`)
  .aggregate(BouncyCastlePlugin.extraProjects.map(_.project) *)

lazy val `scalafix-rules` = (project in file("scalafix/rules"))
  .settings(
    moduleName := "fs2-pgp-scalafix",
    crossScalaVersions := V.ScalafixScalaVersions,
    libraryDependencies ++= Seq(
      "ch.epfl.scala" %% "scalafix-core" % _root_.scalafix.sbt.BuildInfo.scalafixVersion,
    )
  )

// lazy val `scalafix-input` = (project in file("scalafix/input"))
//   .settings(
//     libraryDependencies ++= Seq(
//       "com.dwolla" %% "fs2-pgp" % "0.4.5",
//       "org.typelevel" %% "log4cats-noop" % "2.7.0",
//     ),
//     crossScalaVersions := V.ScalafixScalaVersions,
//     scalacOptions ~= { _.filterNot(_ == "-Xfatal-warnings") },
//     semanticdbEnabled := true,
//     semanticdbVersion := scalafixSemanticdb.revision,
//   )
//   .enablePlugins(NoPublishPlugin)
//
// lazy val `scalafix-output` = (project in file("scalafix/output"))
//   .settings(
//     crossScalaVersions := V.ScalafixScalaVersions,
//     scalacOptions ~= { _.filterNot(_ == "-Xfatal-warnings") },
//   )
//   .dependsOn(LocalProject("fs2-pgp"))
//   .enablePlugins(NoPublishPlugin)
//
// lazy val `scalafix-tests` = (project in file("scalafix/tests"))
//   .settings(
//     crossScalaVersions := V.ScalafixScalaVersions,
//     libraryDependencies += "ch.epfl.scala" % "scalafix-testkit" % _root_.scalafix.sbt.BuildInfo.scalafixVersion % Test cross CrossVersion.full,
//     scalafixTestkitOutputSourceDirectories := (`scalafix-output` / Compile / unmanagedSourceDirectories).value,
//     scalafixTestkitInputSourceDirectories := (`scalafix-input` / Compile / unmanagedSourceDirectories).value,
//     scalafixTestkitInputClasspath := (`scalafix-input` / Compile / fullClasspath).value,
//     scalafixTestkitInputScalacOptions := (`scalafix-input` / Compile / scalacOptions).value,
//     scalafixTestkitInputScalaVersion := (`scalafix-input` / Compile / scalaVersion).value,
//   )
//   .dependsOn(`scalafix-rules`)
//   .enablePlugins(ScalafixTestkitPlugin, NoPublishPlugin)
