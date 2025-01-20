import com.typesafe.tools.mima.plugin.MimaKeys.*
import explicitdeps.ExplicitDepsPlugin.autoImport.*
import org.typelevel.sbt.TypelevelKernelPlugin.autoImport.*
import org.typelevel.sbt.TypelevelSettingsPlugin
import sbt.*
import sbt.Keys.*
import org.typelevel.sbt.NoPublishPlugin

class ArtifactVersions(val latest: ModuleID, compatibleVersions: List[String]) {
  val history: Set[ModuleID] = compatibleVersions.map(latest.withRevision).toSet

  val versions: List[Version] = compatibleVersions.flatMap(Version(_)).sorted.reverse
}

object ArtifactVersions {
  def apply[A](latest: ModuleID, compatibleVersions: List[String]): ArtifactVersions =
    new ArtifactVersions(latest, compatibleVersions)
}

object BouncyCastlePlugin extends AutoPlugin {
  override def trigger = noTrigger

  // When a new version is released, move what was previously the latest version into the list of old versions.
  // This plugin will automatically release a new suffixed artifact that can be used by users with bincompat issues.
  // Don't forget to regenerate the GitHub Actions workflow by running the `githubWorkflowGenerate` sbt task.
  private val bcpg = ArtifactVersions(
    "org.bouncycastle" % "bcpg-jdk18on" % "1.80",
    List(
      "1.79",
      "1.78.1",
      "1.77",
    )
  )

  // bcutil is needed explicitly starting in 1.80 for testing
  // Tests don't get published so w/e
  private val bcutil = ArtifactVersions(
    "org.bouncycastle" % "bcutil-jdk18on" % "1.80" % Test,
    List( 
      "1.79",
      "1.78.1",
      "1.77",
    ) 
  )

  private val commonSettings = Seq(
    libraryDependencies ++= {
      if (tlIsScala3.value) Seq.empty
      else Seq(
        compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.3" cross CrossVersion.full),
        compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      )
    },
    Compile / scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 13 => "-Ymacro-annotations" :: Nil
        case _ => Nil
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n < 13 => compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full) :: Nil
        case _ => Nil
      }
    },
  )

  private def buildProjects(bouncyCastlePg: ModuleID, bouncyCastleUtil: ModuleID, isLatest: Boolean): (Project, Project, Project) = {
    def appendSuffixIfNotLatest(separator: String)
                               (s: String): String =
      if (isLatest) s else s + separator + bouncyCastlePg.revision

    def adjustedFile(s: String): File =
      if (isLatest) file(s) else file(s) / s".bcpg-${bouncyCastlePg.revision}"

    def projectId(s: String): String =
      appendSuffixIfNotLatest("-bcpg")(s).replace('.', '_')

    val core = project
      .in(adjustedFile("core"))
      .withId(projectId("fs2-pgp"))
      .settings(
        name := appendSuffixIfNotLatest("-bcpg")("fs2-pgp"),
        moduleName := s"${name.value}",
        description := appendSuffixIfNotLatest(" ")("fs2 pipes for encrypting and decrypting streams with BouncyCastle PGP"),
        sourceDirectory := (ThisBuild / baseDirectory).value / "core" / "src",
        libraryDependencies ++= {
          Seq(
            "org.typelevel" %% "cats-core" % "2.12.0",
            "org.typelevel" %% "cats-effect" % "3.5.7",
            "co.fs2" %% "fs2-core" % "3.11.0",
            "co.fs2" %% "fs2-io" % "3.11.0",
            "io.monix" %% "newtypes-core" % "0.2.3",
            "org.scala-lang.modules" %% "scala-collection-compat" % "2.12.0",
            "org.typelevel" %% "log4cats-core" % "2.7.0",
            "eu.timepit" %% "refined" % "0.11.3",
            bouncyCastlePg,
          )
        },
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
        mimaBinaryIssueFilters ++= {
          import com.typesafe.tools.mima.core.*
          Seq(
            // the CanCreateDecryptorFactory filters ignore a class and companion object that should have been package private
            // and have been replaced with an alternative implementation that is package private
            ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory"),
            ProblemFilters.exclude[MissingClassProblem]("com.dwolla.security.crypto.CanCreateDecryptorFactory$"),
          )
        },
        mimaPreviousArtifacts := {
          if (isLatest) mimaPreviousArtifacts.value else Set.empty
        }
      )
      .settings(commonSettings)
      .enablePlugins(TypelevelSettingsPlugin)

    val testkit = project
      .in(adjustedFile("testkit"))
      .withId(projectId("pgp-testkit"))
      .settings(
        name := appendSuffixIfNotLatest("-bcpg")("pgp-testkit"),
        moduleName := s"${name.value}",
        description := appendSuffixIfNotLatest(" ")("Scalacheck Arbitraries for BouncyCastle PGP"),
        sourceDirectory := (ThisBuild / baseDirectory).value / "testkit" / "src",
        libraryDependencies ++= {
          Seq(
            "org.scalacheck" %% "scalacheck" % "1.18.0",
            "eu.timepit" %% "refined-scalacheck" % "0.11.2",
            "io.chrisdavenport" %% "cats-scalacheck" % "0.3.2",
          )
        },
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
        mimaPreviousArtifacts := {
          if (isLatest) mimaPreviousArtifacts.value else Set.empty
        }
      )
      .dependsOn(core)
      .settings(commonSettings)
      .enablePlugins(TypelevelSettingsPlugin, NoPublishPlugin)

    val tests = project
      .in(adjustedFile("tests"))
      .withId(projectId("tests"))
      .settings(
        description := "Tests broken out into a separate project to break a circular dependency",
        sourceDirectory := (ThisBuild / baseDirectory).value / "tests" / "src",
        libraryDependencies ++= {
          Seq(
            "org.typelevel" %% "log4cats-noop" % "2.7.0" % Test,
            "org.typelevel" %% "log4cats-slf4j" % "2.7.0" % Test,
            "ch.qos.logback" % "logback-classic" % "1.4.7" % Test,
            "org.scalameta" %% "munit" % "0.7.29" % Test,
            "org.typelevel" %% "scalacheck-effect" % "1.0.4" % Test,
            "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4" % Test,
            "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
            "dev.holt" %% "java-time-literals" % "1.1.1" % Test,
            "com.eed3si9n.expecty" %% "expecty" % "0.16.0" % Test,
            bouncyCastleUtil
          )
        },
        publishArtifact := false,
        unusedCompileDependenciesFilter -= moduleFilter("org.scala-lang.modules", "scala-collection-compat"),
      )
      .dependsOn(core, testkit)
      .settings(commonSettings)
      .enablePlugins(TypelevelSettingsPlugin)

    (core, testkit, tests)
  }

  private val (core, testkit, tests) = buildProjects(bcpg.latest, bcutil.latest, isLatest = true)

  private val oldVersionProjects: List[Project] = bcpg.versions.zip(bcutil.versions).flatMap { case (pgv, utilv) =>
    val (c, tk, t) = buildProjects(bcpg.latest.withRevision(pgv.toString), bcutil.latest.withRevision(utilv.toString), isLatest = false)

    List(c, tk, t)
  }

  override lazy val extraProjects: Seq[Project] =
    Seq(core, testkit, tests) ++ oldVersionProjects
}
