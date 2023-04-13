plugins {
    idea
    scala
    id("com.github.maiflai.scalatest") version "0.32"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.opentelemetry.instrumentation.muzzle-generation") version "1.24.0-alpha"
    id("io.opentelemetry.instrumentation.muzzle-check") version "1.24.0-alpha"
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

sourceSets {
    main {
        scala.setSrcDirs(listOf("src/main/scala", "src/main/java"))
        java.setSrcDirs(emptyList<String>())
    }
    test {
        scala.setSrcDirs(listOf("src/test/scala", "src/test/java", "src/it/scala"))
    }
}

dependencies {
    testImplementation(getDependency("circe-core"))
    testImplementation(getDependency("circe-generic"))
    testImplementation(getDependency("circe-parser"))
    testImplementation(getDependency("circe-yaml"))
    testImplementation(getDependency("logback"))
    testImplementation(getDependency("akka-actor-testkit"))
    testImplementation(getDependency("akka-persistence-testkit"))
    testImplementation(getDependency("akka-stream-testkit"))
    testImplementation(getDependency("akka-http-testkit"))
    testImplementation(getDependency("scalatest"))
    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(getDependency("testcontainers"))
}
