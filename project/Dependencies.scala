import sbt._

object Dependencies {
  val AkkaHttpVersion = "10.1.12"
  val AkkaVersion = "2.6.8"
  val ZioVersion = "1.0.3"
  val CirceVersion = "0.12.3"
  val SlickVersion = "3.3.3"
  val PostgresVersion = "9.4-1201-jdbc41"
  val LogbackVersion = "1.2.3"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson" % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
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

  val openTelemetryApi = Seq("io.opentelemetry" % "opentelemetry-api" % "0.8.0")

  val openTelemetrySdk = Seq("io.opentelemetry" % "opentelemetry-sdk" % "0.8.0")

  val newRelicSdk = Seq(
    "com.newrelic.telemetry" % "opentelemetry-exporters-newrelic" % "0.8.1",
    "com.newrelic.telemetry" % "telemetry" % "0.9.0",
    "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.9.0"
  )

}
