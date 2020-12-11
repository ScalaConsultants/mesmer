import sbt._

object Dependencies {
  val AkkaHttpVersion       = "10.2.0"
  val AkkaVersion           = "2.6.8"
  val CirceVersion          = "0.12.3"
  val SlickVersion          = "3.3.3"
  val PostgresVersion       = "9.4-1201-jdbc41"
  val LogbackVersion        = "1.2.3"
  val ScalatestVersion      = "3.1.2"
  val AkkaManagementVersion = "1.0.9"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-http"                   % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"        % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"                 % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed"          % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed"      % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed"            % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson"  % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
  )

  val akkaPersistance = Seq(
    "com.typesafe.akka"  %% "akka-persistence-typed" % AkkaVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc"  % "4.0.0",
    "com.typesafe.akka"  %% "akka-persistence-query" % AkkaVersion
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % "1.10.18",
    "net.bytebuddy" % "byte-buddy-agent" % "1.10.18"
  )

  val circe =
    Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser"
    ).map(_ % CirceVersion)

  val circeAkka = Seq("de.heikoseeberger" %% "akka-http-circe" % "1.30.0")

  val slick = Seq(
    "com.typesafe.slick" %% "slick"          % SlickVersion,
    "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion
  )

  val postgresDriver = Seq("org.postgresql" % "postgresql" % PostgresVersion)

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq("io.opentelemetry" % "opentelemetry-api" % "0.9.1")

  val openTelemetrySdk = Seq("io.opentelemetry" % "opentelemetry-sdk" % "0.9.1")

  val newRelicSdk = Seq(
    "com.newrelic.telemetry" % "opentelemetry-exporters-newrelic" % "0.9.0",
    "com.newrelic.telemetry" % "telemetry"                        % "0.9.0",
    "com.newrelic.telemetry" % "telemetry-http-okhttp"            % "0.9.0"
  )

  val akkaManagement = Seq(
    "com.lightbend.akka.management" %% "akka-management"              % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % AkkaManagementVersion
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.30")

  val reflection: String => Seq[ModuleID] = version => Seq("org.scala-lang" % "scala-reflect" % version)
}
