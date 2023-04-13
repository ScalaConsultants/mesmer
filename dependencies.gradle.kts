val akkaHttpVersion = "10.4.0"
val akkaHttpCirceVersion = "1.39.2"
val akkaManagementVersion = "1.2.0"
val akkaVersion = "2.7.0"
val autoServiceVersion = "1.0.1"
val byteBuddyVersion = "1.14.2"
val circeVersion = "0.14.5"
val circeYamlVersion = "0.14.2"
val flexmarkVersion = "0.64.0"
val logbackVersion = "1.4.6"
val opentelemetryVersion = "1.24.0"
val opentelemetryAlphaVersion = "1.24.0-alpha"
val postgresqlVersion = "42.6.0"
val scalaVersion = "2.13.10"
val slickVersion = "3.4.1"
val scalatestVersion = "3.2.15"
val testcontainersVersion = "0.40.14"
val zioVersion = "2.0.10"

extra["dependencies"] = mapOf(
    "zio-core" to "dev.zio:zio_2.13:$zioVersion",
    "google-auto-service" to "com.google.auto.service:auto-service:$autoServiceVersion",
    "akka-http" to "com.typesafe.akka:akka-http_2.13:$akkaHttpVersion",
    "akka-http-spray-json" to "com.typesafe.akka:akka-http-spray-json_2.13:$akkaHttpVersion",
    "akka-http-circe" to "de.heikoseeberger:akka-http-circe_2.13:1.39.2",
    "akka-stream" to "com.typesafe.akka:akka-stream_2.13:$akkaVersion",
    "akka-cluster-typed" to "com.typesafe.akka:akka-cluster-typed_2.13:$akkaVersion",
    "akka-persistence-typed" to "com.typesafe.akka:akka-persistence-typed_2.13:$akkaVersion",
    "akka-actor-typed" to "com.typesafe.akka:akka-actor-typed_2.13:$akkaVersion",
    "akka-actor" to "com.typesafe.akka:akka-actor_2.13:$akkaVersion",
    "akka-management" to "com.lightbend.akka.management:akka-management_2.13:$akkaManagementVersion",
    "akka-management-cluster-http" to "com.lightbend.akka.management:akka-management-cluster-http_2.13:$akkaManagementVersion",
    "akka-management-cluster-bootstrap" to "com.lightbend.akka.management:akka-management-cluster-bootstrap_2.13:$akkaManagementVersion",
    "akka-serialization-jackson" to "com.typesafe.akka:akka-serialization-jackson_2.13:$akkaVersion",
    "akka-cluster-sharding-typed" to "com.typesafe.akka:akka-cluster-sharding-typed_2.13:$akkaVersion",
    "akka-actor-testkit" to "com.typesafe.akka:akka-actor-testkit-typed_2.13:$akkaVersion",
    "akka-persistence-testkit" to "com.typesafe.akka:akka-persistence-testkit_2.13:$akkaVersion",
    "akka-stream-testkit" to "com.typesafe.akka:akka-stream-testkit_2.13:$akkaVersion",
    "akka-http-testkit" to "com.typesafe.akka:akka-http-testkit_2.13:$akkaHttpVersion",
    "byte-buddy" to "net.bytebuddy:byte-buddy:$byteBuddyVersion",
    "byte-buddy-agent" to "net.bytebuddy:byte-buddy-agent:$byteBuddyVersion",
    "opentelemetry-instrumentation-api-semconv" to "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv:$opentelemetryAlphaVersion",
    "opentelemetry-javaagent-extension-api" to "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:$opentelemetryAlphaVersion",
    "opentelemetry-instrumentation-api" to "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:$opentelemetryVersion",
    "opentelemetry-javaagent-tooling" to "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling:$opentelemetryAlphaVersion",
    "opentelemetry-muzzle" to "io.opentelemetry.javaagent:opentelemetry-muzzle:$opentelemetryAlphaVersion",
    "opentelemetry-javaagent-bootstrap" to "io.opentelemetry.javaagent:opentelemetry-javaagent-bootstrap:$opentelemetryAlphaVersion",
    "opentelemetry-testing-common" to "io.opentelemetry.javaagent:opentelemetry-testing-common:$opentelemetryAlphaVersion",
    "postgresql" to "org.postgresql:postgresql:$postgresqlVersion",
    "scala-library" to "org.scala-lang:scala-library:$scalaVersion",
    "slick" to "com.typesafe.slick:slick_2.13:$slickVersion",
    "slick-hikaricp" to "com.typesafe.slick:slick-hikaricp_2.13:$slickVersion",
    "scalatest" to "org.scalatest:scalatest_2.13:$scalatestVersion",
    "flexmark" to "com.vladsch.flexmark:flexmark-all:$flexmarkVersion",
    "circe-core" to "io.circe:circe-core_2.13:$circeVersion",
    "circe-generic" to "io.circe:circe-generic_2.13:$circeVersion",
    "circe-parser" to "io.circe:circe-parser_2.13:$circeVersion",
    "circe-yaml" to "io.circe:circe-yaml_2.13:$circeYamlVersion",
    "logback" to "ch.qos.logback:logback-classic:$logbackVersion",
    "testcontainers" to "com.dimafeng:testcontainers-scala-scalatest_2.13:$testcontainersVersion"
)

extra["getDependency"] = fun(name: String): String {
    val dependencies: Map<String, String> = extra["dependencies"] as Map<String, String>
    return dependencies[name]!!
}
