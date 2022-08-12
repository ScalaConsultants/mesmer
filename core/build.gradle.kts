plugins {
    java
    idea
    scala
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

dependencies {
    implementation(getDependency("akka-http"))
    implementation(getDependency("akka-http-spray-json"))
    implementation(getDependency("akka-stream"))
    implementation(getDependency("akka-cluster-typed"))
    implementation(getDependency("akka-persistence-typed"))
    implementation(getDependency("akka-actor-typed"))
    implementation(getDependency("akka-actor"))
    implementation(getDependency("akka-serialization-jackson"))
    implementation(getDependency("akka-cluster-sharding-typed"))
    implementation(getDependency("opentelemetry-instrumentation-api"))
}
