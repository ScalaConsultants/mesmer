import Dependencies._
import sbt.Package.{ MainClass, ManifestAttributes }

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.scalac"
ThisBuild / organizationName := "scalac"

def runWithAgent = Command.command("runWithAgent") { state =>
  val extracted = Project extract state
  val newState =
    extracted.appendWithSession(Seq(run / javaOptions += s"-javaagent:${(agent / assembly).value.absolutePath}"), state)
  val (s, _) = Project.extract(newState).runInputTask(Compile / run, "", newState)
  s
}

lazy val root = (project in file("."))
  .settings(
    name := "akka-monitoring"
  )
  .aggregate(extension, agent, testApp)

lazy val extension = (project in file("extension"))
  .configs()
  .settings(
    name := "akka-monitoring-extension",
    libraryDependencies ++= akka ++ openTelemetryApi ++ akkaTestkit ++ scalatest ++ logback.map(_ % Test)
  )

val assemblyMergeStrategySettings = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", _ @_*)                      => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)                                => MergeStrategy.discard
  case PathList("reference.conf")                                   => MergeStrategy.concat
  case PathList("jackson-annotations-2.10.3.jar", _ @_*)            => MergeStrategy.last
  case PathList("jackson-core-2.10.3.jar", _ @_*)                   => MergeStrategy.last
  case PathList("jackson-databind-2.10.3.jar", _ @_*)               => MergeStrategy.last
  case PathList("jackson-dataformat-cbor-2.10.3.jar", _ @_*)        => MergeStrategy.last
  case PathList("jackson-datatype-jdk8-2.10.3.jar", _ @_*)          => MergeStrategy.last
  case PathList("jackson-datatype-jsr310-2.10.3.jar", _ @_*)        => MergeStrategy.last
  case PathList("jackson-module-parameter-names-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-module-paranamer-2.10.3.jar", _ @_*)       => MergeStrategy.last
  case _                                                            => MergeStrategy.first
}

lazy val agent = (project in file("agent"))
  .settings(
    name := "akka-monitoring-agent",
    libraryDependencies ++= akka ++ byteBuddy ++ scalatest,
    Compile / mainClass := Some("io.scalac.agent.Boot"),
    Compile / packageBin / packageOptions := {
      (Compile / packageBin / packageOptions).value.map {
        case MainClass(mainClassName) => ManifestAttributes(List("Premain-Class" -> mainClassName): _*)
        case other                    => other
      }
    },
    assembly / test := {},
    assembly / assemblyJarName := "scalac_agent.jar",
    assemblyMergeStrategySettings,
    Test / fork := true
  )

lazy val testApp = (project in file("test_app"))
  .settings(
    name := "akka-monitoring-test-app",
    libraryDependencies ++= akka ++ scalatest ++ akkaTestkit ++ zio ++ circe ++ circeAkka ++ postgresDriver ++ akkaPersistance ++ slick ++ logback ++ newRelicSdk,
    assemblyMergeStrategySettings,
    assembly / mainClass := Some("io.scalac.Boot"),
    assembly / assemblyJarName := "test_app.jar",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    run / fork := true,
    run / connectInput := true,
    commands += runWithAgent
  )
  .dependsOn(extension, agent)
