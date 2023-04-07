import java.nio.file.Files

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
}

tasks.register("createMainrawDirectory") {
    val directory = file("$buildDir/classes/java/mainraw")

    doLast {
        Files.createDirectories(directory.toPath())
    }
}

tasks.named("byteBuddyJava") {
    dependsOn("createMainrawDirectory")
}

tasks.named("byteBuddyScala") {
    dependsOn("createMainrawDirectory")
}

dependencies {
    implementation(getDependency("scala-library"))
    implementation(getDependency("scala-library"))
    implementation(getDependency("zio-core"))
    implementation(getDependency("opentelemetry-javaagent-tooling"))
    implementation(getDependency("opentelemetry-javaagent-extension-api"))
    implementation(getDependency("opentelemetry-muzzle"))
    implementation(getDependency("opentelemetry-javaagent-bootstrap"))
    implementation(getDependency("opentelemetry-instrumentation-api-semconv"))
    implementation(getDependency("byte-buddy"))
    implementation(getDependency("byte-buddy-agent"))
    compileOnly(getDependency("google-auto-service"))
    annotationProcessor(getDependency("google-auto-service"))
    implementation(project(":core"))
    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(getDependency("scalatest"))

}
