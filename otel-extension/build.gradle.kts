plugins {
    java
    idea
    scala
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

dependencies {
    compileOnly(getDependency("zio-core"))
    compileOnly(getDependency("opentelemetry-extension-api"))
    compileOnly(getDependency("opentelemetry-javaagent-tooling"))
    compileOnly(getDependency("opentelemetry-muzzle"))
    compileOnly(getDependency("opentelemetry-javaagent-bootstrap"))
    compileOnly(getDependency("opentelemetry-instrumentation-api"))
    compileOnly(getDependency("opentelemetry-instrumentation-api-semconv"))
    compileOnly(getDependency("byte-buddy"))
    compileOnly(getDependency("byte-buddy-agent"))
    compileOnly(getDependency("google-auto-service"))
}
