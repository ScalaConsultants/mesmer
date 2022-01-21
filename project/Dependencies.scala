import sbt._

object Dependencies {

  val AkkaHttpVersion                     = "10.2.7"
  val AkkaManagementVersion               = "1.1.2"
  val AkkaVersion                         = "2.6.18"
  val CirceVersion                        = "0.14.1"
  val LogbackVersion                      = "1.2.10"
  val OpentelemetryVersion                = "1.10.1"
  val OpentelemetryMetricsApiVersion      = "1.10.0-alpha-rc.1"
  val OpentelemetryMetricsExporterVersion = "1.10.0-alpha"
  val PostgresVersion                     = "42.3.1"
  val ScalatestVersion                    = "3.2.10"
  val SlickVersion                        = "3.3.3"

  val akka = Seq(
    "com.typesafe.akka" %% "akka-http"                   % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json"        % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-stream"                 % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed"          % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-typed"      % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor-typed"            % AkkaVersion,
    "com.typesafe.akka" %% "akka-actor"                  % AkkaVersion,
    "com.typesafe.akka" %% "akka-serialization-jackson"  % AkkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion
  )

  val akkaPersistance = Seq(
    "com.typesafe.akka"  %% "akka-persistence-typed" % AkkaVersion,
    "com.lightbend.akka" %% "akka-persistence-jdbc"  % "5.0.4",
    "com.typesafe.akka"  %% "akka-persistence-query" % AkkaVersion
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % "1.12.7",
    "net.bytebuddy" % "byte-buddy-agent" % "1.12.7"
  )

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % OpentelemetryVersion
  )

  val openTelemetryApiMetrics = Seq(
    "io.opentelemetry" % "opentelemetry-api-metrics" % OpentelemetryMetricsApiVersion
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.33")

  val reflection: String => Seq[ModuleID] = version => Seq("org.scala-lang" % "scala-reflect" % version)

  val exampleDependencies = Seq(
    "io.circe"                      %% "circe-core"                          % CirceVersion,
    "io.circe"                      %% "circe-generic"                       % CirceVersion,
    "io.circe"                      %% "circe-parser"                        % CirceVersion,
    "de.heikoseeberger"             %% "akka-http-circe"                     % "1.39.2",
    "org.postgresql"                 % "postgresql"                          % PostgresVersion,
    "com.typesafe.slick"            %% "slick"                               % SlickVersion,
    "com.typesafe.slick"            %% "slick-hikaricp"                      % SlickVersion,
    "com.typesafe.akka"             %% "akka-discovery"                      % AkkaVersion,
    "com.lightbend.akka.management" %% "akka-management"                     % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http"        % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap"   % AkkaManagementVersion,
    "io.opentelemetry"               % "opentelemetry-exporter-otlp-metrics" % OpentelemetryMetricsExporterVersion,
    "io.opentelemetry"               % "opentelemetry-sdk"                   % OpentelemetryVersion,
    "io.grpc"                        % "grpc-netty-shaded"                   % "1.43.2"
  )
}
