import sbt._

object Versions {
  val AkkaHttpVersion       = "10.2.0"
  val AkkaVersion           = "2.6.8"
  val LogbackVersion        = "1.2.3"
  val ScalatestVersion      = "3.1.2"
}

object Dependencies {  

  import Versions._
  
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
    "com.lightbend.akka" %% "akka-persistence-jdbc"  % "4.0.0",
    "com.typesafe.akka"  %% "akka-persistence-query" % AkkaVersion
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % "1.10.18",
    "net.bytebuddy" % "byte-buddy-agent" % "1.10.18"
  )


  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % "0.13.0"
  )

  val openTelemetrySdk = Seq(
    "io.opentelemetry" % "opentelemetry-sdk" % "0.13.0"
  )

  val openTelemetryDependenciesOverrides = Seq(
    "io.opentelemetry" % "opentelemetry-api-metrics" % "0.13.0-alpha",
    "io.opentelemetry" % "opentelemetry-sdk-metrics" % "0.13.0-alpha"
  )

  val newRelicSdk = Seq(
    "com.newrelic.telemetry" % "opentelemetry-exporters-newrelic" % "0.13.1",
    // TODO uncomment the line above when solve this issue: https://github.com/newrelic/opentelemetry-exporter-java/issues/149
    // For a while, we use it as a unmanaged dependency at extension/lib
    "com.newrelic.telemetry" % "telemetry"             % "0.10.0",
    "com.newrelic.telemetry" % "telemetry-http-okhttp" % "0.10.0"
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.30")

  val reflection: String => Seq[ModuleID] = version => Seq("org.scala-lang" % "scala-reflect" % version)

  val prometheus = Seq(
    "io.opentelemetry" % "opentelemetry-exporter-prometheus" % "0.13.1",
    "fr.davit"         %% "akka-http-metrics-prometheus"     % "1.1.1"
  )
}
