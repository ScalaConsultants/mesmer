import sbt._

object Dependencies {

  val AirframeVersion              = "22.8.0"
  val AkkaHttpVersion              = "10.2.9"
  val AkkaManagementVersion        = "1.1.3"
  val AkkaVersion                  = "2.6.19"
  val CirceVersion                 = "0.14.2"
  val GoogleAutoServiceVersion     = "1.0.1"
  val LogbackVersion               = "1.2.11"
  val OpentelemetryLatestVersion   = "1.13.1"
  val OpentelemetryApiVersion      = "1.13.0"
  val OpentelemetryAlphaVersion131 = "1.13.1-alpha"
  val OpentelemetryAlphaVersion130 = "1.13.0-alpha"
  val PostgresVersion              = "42.4.2"
  val ScalatestVersion             = "3.2.14"
  val SlickVersion                 = "3.3.3"

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

  val zio = Seq(
    "dev.zio" %% "zio" % "2.0.0"
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % "1.12.12",
    "net.bytebuddy" % "byte-buddy-agent" % "1.12.12"
  )

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % OpentelemetryApiVersion
  )

  val openTelemetryInstrumentation = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-instrumentation-api" % OpentelemetryAlphaVersion131
  )

  val openTelemetryInstrumentationApiSemanticConventions = Seq(
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api-semconv" % OpentelemetryAlphaVersion131
  )

  val openTelemetryExtension = Seq(
    "com.google.auto.service"    % "auto-service"                          % GoogleAutoServiceVersion,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-extension-api" % OpentelemetryAlphaVersion131,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-tooling"       % OpentelemetryAlphaVersion131
  )

  val openTelemetryMuzzle = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-muzzle"              % OpentelemetryAlphaVersion131,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-bootstrap" % OpentelemetryAlphaVersion131
  )

  val openTelemetryTesting = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-testing-common" % OpentelemetryAlphaVersion131
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion)
}
