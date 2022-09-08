import sbt._

object Dependencies {

  val AirframeVersion              = "22.8.0"
  val AkkaHttpVersion              = "10.2.9"
  val AkkaManagementVersion        = "1.1.3"
  val AkkaVersion                  = "2.6.19"
  val CirceVersion                 = "0.14.2"
  val GoogleAutoServiceVersion     = "1.0.1"
  val LogbackVersion               = "1.2.11"
  val OpentelemetryLatestVersion   = "1.17.0"
  val OpentelemetryApiVersion      = "1.17.0"
  val OpentelemetryAlphaVersion170 = "1.17.0-alpha"
  val PostgresVersion              = "42.4.0"
  val ScalatestVersion             = "3.2.13"
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

  val openTelemetryInstrumentationApiSemanticConventions = Seq(
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api-semconv" % OpentelemetryAlphaVersion170
  )

  val openTelemetryExtensionApi = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-extension-api" % OpentelemetryAlphaVersion170
  )

  val openTelemetryExtension = Seq(
    "com.google.auto.service"    % "auto-service"                          % GoogleAutoServiceVersion,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-extension-api" % OpentelemetryAlphaVersion170,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-tooling"       % OpentelemetryAlphaVersion170
  )

  val openTelemetryMuzzle = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-muzzle"              % OpentelemetryAlphaVersion170,
    "io.opentelemetry.javaagent" % "opentelemetry-javaagent-bootstrap" % OpentelemetryAlphaVersion170
  )

  val openTelemetryTesting = Seq(
    "io.opentelemetry.javaagent" % "opentelemetry-testing-common" % OpentelemetryAlphaVersion170
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
