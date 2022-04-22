import sbt._

object Dependencies {

  val AirframeVersion                = "22.4.2"
  val AkkaHttpVersion                = "10.2.9"
  val AkkaManagementVersion          = "1.1.3"
  val AkkaVersion                    = "2.6.19"
  val CirceVersion                   = "0.14.1"
  val GoogleAutoServiceVersion       = "1.0.1"
  val LogbackVersion                 = "1.2.11"
  val OpentelemetryVersion           = "1.10.0"
  val OpentelemetryAlphaVersion      = "1.10.0-alpha"
  val OpentelemetryMetricsApiVersion = "1.10.0-alpha-rc.1"
  val PostgresVersion                = "42.3.4"
  val ScalatestVersion               = "3.2.11"
  val SlickVersion                   = "3.3.3"

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
    "net.bytebuddy" % "byte-buddy"       % "1.12.9",
    "net.bytebuddy" % "byte-buddy-agent" % "1.12.9"
  )

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % OpentelemetryVersion
  )

  val openTelemetryApiMetrics = Seq(
    "io.opentelemetry" % "opentelemetry-api-metrics" % OpentelemetryMetricsApiVersion
  )

  val openTelemetryInstrumentationApi = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-instrumentation-api" % OpentelemetryAlphaVersion
  )

  val openTelemetryInstrumentation = Seq(
    "com.google.auto.service"    % "auto-service"                          % GoogleAutoServiceVersion,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-extension-api" % OpentelemetryAlphaVersion
  )

  val openTelemetryMuzzle = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-muzzle" % OpentelemetryAlphaVersion
  )
  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test)

  val exampleDependencies = Seq(
    "io.circe"                      %% "circe-core"                                % CirceVersion,
    "io.circe"                      %% "circe-generic"                             % CirceVersion,
    "io.circe"                      %% "circe-parser"                              % CirceVersion,
    "de.heikoseeberger"             %% "akka-http-circe"                           % "1.39.2",
    "org.postgresql"                 % "postgresql"                                % PostgresVersion,
    "com.typesafe.slick"            %% "slick"                                     % SlickVersion,
    "com.typesafe.slick"            %% "slick-hikaricp"                            % SlickVersion,
    "com.typesafe.akka"             %% "akka-discovery"                            % AkkaVersion,
    "com.lightbend.akka.management" %% "akka-management"                           % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http"              % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap"         % AkkaManagementVersion,
    "io.opentelemetry"               % "opentelemetry-exporter-otlp-metrics"       % OpentelemetryAlphaVersion,
    "io.opentelemetry"               % "opentelemetry-sdk-extension-autoconfigure" % OpentelemetryAlphaVersion,
    "io.opentelemetry"               % "opentelemetry-sdk"                         % OpentelemetryVersion,
    "io.grpc"                        % "grpc-netty-shaded"                         % "1.45.1",
    "org.wvlet.airframe"            %% "airframe-log"                              % AirframeVersion
  )
}
