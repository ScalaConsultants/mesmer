import Dependencies._

ThisBuild / scalaVersion := "2.12.10"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "io.scalac"
ThisBuild / organizationName := "scalac"

ThisBuild / assembly / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

lazy val root = (project in file("."))
  .settings(
    name := "akka-monitoring",
  ).aggregate(extension, testApp)

lazy val extension = (project in file("extension"))
  .settings(
    name := "akka-monitoring-extension",
    libraryDependencies ++= akka ++ openTelemetryApi
  )
lazy val testApp = (project in file("test_app"))
  .settings(
    name := "akka-monitoring-test-app",
    libraryDependencies ++= akka ++ zio ++ circe ++ circeAkka ++ postgresDriver ++ slick ++ logback ++ newRelicSdk ,
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "services", _ @ _*) => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case PathList("jackson-annotations-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-core-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-databind-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-dataformat-cbor-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-datatype-jdk8-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-datatype-jsr310-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-module-parameter-names-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case PathList("jackson-module-paranamer-2.10.3.jar", _ @ _*) => MergeStrategy.last
      case _ => MergeStrategy.first

    },
    assembly / mainClass := Some("io.scalac.Boot"),
    assembly / assemblyJarName := "test_app.jar",
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  ).dependsOn(extension)
