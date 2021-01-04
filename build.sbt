import Dependencies._
import sbt.Package.{ MainClass, ManifestAttributes }

ThisBuild / scalaVersion := "2.13.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.scalac"
ThisBuild / organizationName := "scalac"

def runWithAgent = Command.command("runWithAgent") { state =>
  val extracted = Project extract state
  val newState =
    extracted.appendWithSession(
      Seq(
        run / javaOptions += s"-javaagent:${(agent / assembly).value.absolutePath}"
      ),
      state
    )
  val (s, _) =
    Project.extract(newState).runInputTask(Compile / run, "", newState)
  s
}
lazy val root = (project in file("."))
//  .enablePlugins(MultiJvmPlugin)
  .settings(name := "akka-monitoring")
  .aggregate(extension, agent, testApp)

lazy val core = (project in file("core"))
  .settings(
    name := "core",
    libraryDependencies ++= akka ++ openTelemetryApi ++ scalatest ++ akkaTestkit
  )

lazy val extension = (project in file("extension"))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(
    parallelExecution in Test := true,
    name := "akka-monitoring-extension",
    libraryDependencies ++= akka ++ openTelemetryApi ++ akkaTestkit ++ scalatest ++ logback.map(_ % Test) ++ akkaMultiNodeTestKit
       ++ newRelicSdk ++ openTelemetrySdk
  )
  .dependsOn(core)

val assemblyMergeStrategySettings = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", _ @_*)           => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)                     => MergeStrategy.discard
  case PathList("reference.conf")                        => MergeStrategy.concat
  case PathList("jackson-annotations-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-core-2.10.3.jar", _ @_*)        => MergeStrategy.last
  case PathList("jackson-databind-2.10.3.jar", _ @_*)    => MergeStrategy.last
  case PathList("jackson-dataformat-cbor-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-datatype-jdk8-2.10.3.jar", _ @_*) => MergeStrategy.last
  case PathList("jackson-datatype-jsr310-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-parameter-names-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case PathList("jackson-module-paranamer-2.10.3.jar", _ @_*) =>
    MergeStrategy.last
  case _ => MergeStrategy.first
}

lazy val agent = (project in file("agent"))
  .settings(
    name := "akka-monitoring-agent",
    libraryDependencies ++= akka.map(_ % "provided") ++ byteBuddy ++ scalatest ++ akkaTestkit ++ slf4jApi ++ reflection(
      scalaVersion.value
    ),
    Compile / mainClass := Some("io.scalac.agent.Boot"),
    Compile / packageBin / packageOptions := {
      (Compile / packageBin / packageOptions).value.map {
        case MainClass(mainClassName) =>
          ManifestAttributes(List("Premain-Class" -> mainClassName): _*)
        case other => other
      }
    },
    assembly / test := {},
    assembly / assemblyJarName := "scalac_agent.jar",
    assembly / assemblyOption ~= { _.copy(includeScala = false) },
    assemblyMergeStrategySettings,
    Test / fork := true
  )
  .dependsOn(
    core % "provided->compile"
  )

lazy val testApp = (project in file("test_app"))
  .settings(
    name := "akka-monitoring-test-app",
    libraryDependencies ++= akka ++ scalatest ++ akkaTestkit ++ circe ++ circeAkka ++ postgresDriver ++ akkaPersistance ++ slick ++ logback ++ newRelicSdk ++ akkaManagement,
    assemblyMergeStrategySettings,
    assembly / mainClass := Some("io.scalac.Boot"),
    assembly / assemblyJarName := "test_app.jar",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    run / fork := true,
    run / javaOptions ++= {
      val properties = System.getProperties

      import scala.collection.JavaConverters._
      (for {
        (key, value) <- properties.asScala.toList if value.nonEmpty
      } yield s"-D$key=$value")
    },
    commands += runWithAgent
  )
  .dependsOn(extension, agent)
