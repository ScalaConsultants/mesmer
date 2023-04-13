plugins {
    java
    idea
    scala
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

dependencies {
    implementation(getDependency("scala-library"))
    compileOnly(getDependency("akka-actor-testkit"))
    compileOnly(getDependency("akka-persistence-testkit"))
    compileOnly(getDependency("akka-stream-testkit"))
    compileOnly(getDependency("akka-http-testkit"))
    compileOnly(getDependency("scalatest"))
}
