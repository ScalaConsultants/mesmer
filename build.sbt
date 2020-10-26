import Dependencies._

ThisBuild / scalaVersion := "2.13.3"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "akka-monitoring",
  ).aggregate(extension, testApp)

lazy val extension = (project in file("extension"))
  .settings(
    name := "akka-monitoring-extension",
    libraryDependencies ++= akka
  )
lazy val testApp = (project in file("test_app"))
  .settings(
    name := "akka-monitoring-test-app",
    libraryDependencies ++= akka ++ zio ++ circe ++ circeAkka ++ postgresDriver ++ slick ++ logback
  ).dependsOn(extension)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
