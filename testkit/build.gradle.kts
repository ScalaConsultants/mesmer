plugins {
    java
    idea
    scala
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

dependencies {
    implementation(getDependency("scala-library"))
    compileOnly(getDependency("actor-testkit"))
    compileOnly(getDependency("persistence-testkit"))
    compileOnly(getDependency("stream-testkit"))
    compileOnly(getDependency("http-testkit"))
    compileOnly(getDependency("scalatest"))
}
