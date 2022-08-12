val akkaHttpVersion = "10.2.9"
val akkaVersion = "2.6.19"
val autoServiceVersion = "1.0.1"
val byteBuddyVersion = "1.12.13"
val opentelemetryVersion = "1.13.0"
val opentelemetryAlphaVersion = "1.13.1-alpha"
val scalaVersion = "2.13.6"

val zioVersion = "2.0.0"

val zio = mapOf(
    "zio-core" to "dev.zio:zio_3:$zioVersion"
)
val google = mapOf(
    "google-auto-service" to "com.google.auto.service:auto-service:$autoServiceVersion"
)

val akka = mapOf(
    "akka-http" to "com.typesafe.akka:akka-http_2.13:$akkaHttpVersion",
    "akka-http-spray-json" to "com.typesafe.akka:akka-http-spray-json_2.13:$akkaHttpVersion",
    "akka-stream" to "com.typesafe.akka:akka-stream_2.13:$akkaVersion",
    "akka-cluster-typed" to "com.typesafe.akka:akka-cluster-typed_2.13:$akkaVersion",
    "akka-persistence-typed" to "com.typesafe.akka:akka-persistence-typed_2.13:$akkaVersion",
    "akka-actor-typed" to "com.typesafe.akka:akka-actor-typed_2.13:$akkaVersion",
    "akka-actor" to "com.typesafe.akka:akka-actor_2.13:$akkaVersion",
    "akka-serialization-jackson" to "com.typesafe.akka:akka-serialization-jackson_2.13:$akkaVersion",
    "akka-cluster-sharding-typed" to "com.typesafe.akka:akka-cluster-sharding-typed_2.13:$akkaVersion"
)

val byteBuddy = mapOf(
    "byte-buddy" to "net.bytebuddy:byte-buddy:$byteBuddyVersion",
    "byte-buddy-agent" to "net.bytebuddy:byte-buddy-agent:$byteBuddyVersion"
)

val openTelemetry = mapOf(
    "opentelemetry-api" to "io.opentelemetry:opentelemetry-api:$opentelemetryVersion",
    "opentelemetry-instrumentation-api" to "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api:$opentelemetryAlphaVersion",
    "opentelemetry-extension-api" to "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api:$opentelemetryAlphaVersion",
    "opentelemetry-javaagent-tooling" to "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling:$opentelemetryAlphaVersion",
    "opentelemetry-muzzle" to "io.opentelemetry.javaagent:opentelemetry-muzzle:$opentelemetryAlphaVersion",
    "opentelemetry-javaagent-bootstrap" to "io.opentelemetry.javaagent:opentelemetry-javaagent-bootstrap:$opentelemetryAlphaVersion",
    "opentelemetry-instrumentation-api-semconv" to "io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-semconv:$opentelemetryAlphaVersion"

)
val scala = mapOf(
    "scala-library" to "org.scala-lang:scala-library:$scalaVersion"
)


extra["dependencies"] = akka + openTelemetry + scala + google + zio + byteBuddy
extra["getDependency"] = fun(name: String): String {
    val dependencies: Map<String, String> = extra["dependencies"] as Map<String, String>
    return dependencies[name]!!
}
