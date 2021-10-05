import sbt._

object Dependencies {  

  val AkkaHttpVersion             = "10.2.4"
  val AkkaManagementVersion       = "1.1.1"
  val AkkaVersion                 = "2.6.15"
  val CirceVersion                = "0.14.1"
  val LogbackVersion              = "1.2.3"
  val OpentelemetryVersion        = "1.4.1"
  val OpentelemetryMetricsVersion = "1.2.0-alpha"
  val PostgresVersion             = "42.2.23"
  val ScalatestVersion            = "3.2.9"
  val SlickVersion                = "3.3.3"
  
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
    "com.lightbend.akka" %% "akka-persistence-jdbc"  % "5.0.1",
    "com.typesafe.akka"  %% "akka-persistence-query" % AkkaVersion
  )

  val byteBuddy = Seq(
    "net.bytebuddy" % "byte-buddy"       % "1.11.19",
    "net.bytebuddy" % "byte-buddy-agent" % "1.11.19"
  )

  val logback = Seq("ch.qos.logback" % "logback-classic" % LogbackVersion)

  val openTelemetryApi = Seq(
    "io.opentelemetry" % "opentelemetry-api" % OpentelemetryVersion
  )

  val openTelemetryApiMetrics = Seq(
    "io.opentelemetry" % "opentelemetry-api-metrics" % OpentelemetryMetricsVersion
  )

  val akkaTestkit = Seq(
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-stream-testkit"      % AkkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"        % AkkaHttpVersion % Test
  )

  val scalatest = Seq("org.scalatest" %% "scalatest" % ScalatestVersion % Test)

  val akkaMultiNodeTestKit = Seq("com.typesafe.akka" %% "akka-multi-node-testkit" % AkkaVersion % Test)

  val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.32")

  val reflection: String => Seq[ModuleID] = version => Seq("org.scala-lang" % "scala-reflect" % version)

  val exampleDependencies = Seq(
    "io.circe"                      %% "circe-core"                         % CirceVersion,
    "io.circe"                      %% "circe-generic"                      % CirceVersion,
    "io.circe"                      %% "circe-parser"                       % CirceVersion,
    "de.heikoseeberger"             %% "akka-http-circe"                    % "1.37.0",
    "org.postgresql"                %  "postgresql"                         % PostgresVersion,
    "com.typesafe.slick"            %% "slick"                              % SlickVersion,
    "com.typesafe.slick"            %% "slick-hikaricp"                     % SlickVersion,
    "com.typesafe.akka"             %% "akka-discovery"                     % AkkaVersion,
    "com.lightbend.akka.management" %% "akka-management"                    % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http"       % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap"  % AkkaManagementVersion,
    "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"      % AkkaManagementVersion,
    "io.opentelemetry"              % "opentelemetry-exporter-otlp-metrics" % OpentelemetryMetricsVersion,
    "io.grpc"                       % "grpc-netty-shaded"                   % "1.39.0"
  )
}
