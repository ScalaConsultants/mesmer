import java.nio.file.Files

plugins {
    idea
    scala
    id("com.github.maiflai.scalatest") version "0.32"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("io.opentelemetry.instrumentation.muzzle-generation") version "1.24.0-alpha"
    id("io.opentelemetry.instrumentation.muzzle-check") version "1.24.0-alpha"
}

val extensionArtifact by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(extensionArtifact.name, tasks.shadowJar)
}


apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

sourceSets {
    main {
        scala.setSrcDirs(listOf("src/main/scala", "src/main/java"))
        java.setSrcDirs(emptyList<String>())
    }
    test {
        scala.setSrcDirs(listOf("src/test/scala", "src/test/java"))
    }

    create("integrationTest") {
        scala.setSrcDirs(listOf("src/it/scala", "src/it/java"))

        //TODO: DOES THIS MAKE SENSE??? Or should it be testCompileClasspath???
        compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
        runtimeClasspath += output + compileClasspath + sourceSets["test"].runtimeClasspath
    }
}

task<Test>("integrationTest") {
    description = "Runs the integration tests"
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath

    jvmArgs = listOf(
        //"-javaagent:../opentelemetry-agent-for-testing-1.24.0-alpha.jar",
        "-Dotel.javaagent.extensions=build/libs/otel-extension-all.jar",
        "-Dotel.javaagent.debug=true",
        "-Dotel.javaagent.testing.fail-on-context-leak=true",
        "-Dotel.javaagent.testing.transform-safe-logging.enabled=true",
        "-Dotel.javaagent.testing.exporter.temporality=CUMULATIVE",
        "-Dmesmer.akka.persistence.templated=false",
        "-Dotel.java.global-autoconfigure.enabled=true",

        // suppress repeated logging of "No metric data to export - skipping export."
        // since PeriodicMetricReader is configured with a short interval
        "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.opentelemetry.sdk.metrics.export.PeriodicMetricReader=INFO",

        // suppress a couple of verbose ClassNotFoundException stack traces logged at debug level
        "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ServerImplBuilder=INFO",
        "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.internal.ManagedChannelImplBuilder=INFO",
        "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.perfmark.PerfMark=INFO",
        "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.log.io.grpc.Context=INFO"
    )

    mustRunAfter(tasks["test"])
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

//configurations {
//    runtimeClasspath {
//        exclude(group = "io.opentelemetry.javaagent", module = "opentelemetry-javaagent-bootstrap")
//    }
//}

dependencies {
    implementation(getDependency("scala-library"))
    implementation(getDependency("zio-core"))
    implementation(getDependency("opentelemetry-javaagent-tooling"))
    implementation(getDependency("opentelemetry-javaagent-extension-api"))
    implementation(getDependency("opentelemetry-instrumentation-api-semconv"))
    implementation(getDependency("byte-buddy"))
    implementation(getDependency("byte-buddy-agent"))
    compileOnly(getDependency("google-auto-service"))
    annotationProcessor(getDependency("google-auto-service"))
    implementation(project(":core"))
    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(project(":testkit"))
    testImplementation(getDependency("akka-actor-testkit"))
    testImplementation(getDependency("akka-persistence-testkit"))
    testImplementation(getDependency("akka-stream-testkit"))
    testImplementation(getDependency("akka-http-testkit"))
    testImplementation(getDependency("scalatest"))
    testImplementation(getDependency("opentelemetry-testing-common"))
}
