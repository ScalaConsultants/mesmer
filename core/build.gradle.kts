plugins {
    java
    `java-library`
    idea
    scala
    id("com.github.maiflai.scalatest") version "0.32"
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

dependencies {
    api(getDependency("akka-http"))
    api(getDependency("akka-persistence-typed"))
    api(getDependency("akka-cluster-sharding-typed"))

    implementation(getDependency("scala-library"))
    implementation(getDependency("akka-http-spray-json"))
    implementation(getDependency("akka-stream"))
    implementation(getDependency("akka-cluster-typed"))
    implementation(getDependency("akka-actor-typed"))
    implementation(getDependency("akka-actor"))
    implementation(getDependency("akka-serialization-jackson"))
    implementation(getDependency("opentelemetry-instrumentation-api"))
    implementation(getDependency("opentelemetry-javaagent-extension-api"))
    testImplementation(getDependency("scalatest"))
    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(project(":testkit"))
}
