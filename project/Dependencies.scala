import sbt._

object Dependencies {
  val AkkaHttpVersion = "10.2.0"
  val AkkaVersion = "2.6.8"
  val ZioVersion = "1.0.3"
  val CirceVersion = "0.12.3"
  val SlickVersion = "3.3.3"
  val PostgresVersion = "9.4-1201-jdbc41"
  val LogbackVersion = "1.2.3"
  val ScalatestVersion = "3.1.2"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
  )

  val akkaPersistance = Seq(
    "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc" % "4.0.0",
    "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy" % "1.10.18",
    "net.bytebuddy" % "byte-buddy-agent" % "1.10.18"
  )

  val zio = Seq(
    "dev.zio" %% "zio" % ZioVersion,
    "dev.zio" %% "zio-streams" % ZioVersion
  )

  val circe =
    Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % CirceVersion)

  val circeAkka = Seq("de.heikoseeberger" %% "akka-http-circe" % "1.30.0")

  val slick = Seq(
    "com.typesafe.slick" %% "slick" % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion
  )
  val postgresDriver = Seq("org.postgresql" % "postgresql" % PostgresVersion)

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq("io.opentelemetry" % "opentelemetry-api" % "0.9.1")

  val openTelemetrySdk = Seq("io.opentelemetry" % "opentelemetry-sdk" % "0.9.1")

  val newRelicSdk = Seq(
    "com.newrelic.telemetry" % "opentelemetry-exporters-newrelic" % "0.9.0",
    "com.newrelic.telemetry" % "telemetry" % "0.9.0",
    "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.9.0"
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)
}
