plugins {
    idea
    scala
    application
}

apply(from = "../../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

application {
    mainClass.set("example.SimpleZioExample")
}

tasks.withType<JavaExec>() {
    standardInput = System.`in`
}

dependencies {
    implementation(getDependency("zio-core"))
    implementation(getDependency("logback"))
}
