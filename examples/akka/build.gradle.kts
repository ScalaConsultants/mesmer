plugins {
    idea
    scala
    application
    id("com.github.maiflai.scalatest") version "0.32"
}

apply(from = "../../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

val otelExtensionArtifact by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    otelExtensionArtifact(project(path = ":otel-extension", configuration = "extensionArtifact"))
    implementation(getDependency("scala-library"))
    implementation(getDependency("logback"))
    implementation(getDependency("akka-http"))
    implementation(getDependency("akka-http-spray-json"))
    implementation(getDependency("akka-stream"))
    implementation(getDependency("akka-cluster-typed"))
    implementation(getDependency("akka-persistence-typed"))
    implementation(getDependency("akka-persistence-jdbc"))
    implementation(getDependency("akka-persistence-query"))
    implementation(getDependency("akka-actor-typed"))
    implementation(getDependency("akka-actor"))
    implementation(getDependency("akka-serialization-jackson"))
    implementation(getDependency("akka-cluster-sharding-typed"))
    implementation(getDependency("circe-core"))
    implementation(getDependency("circe-generic"))
    implementation(getDependency("circe-parser"))
    implementation(getDependency("circe-yaml"))
    implementation(getDependency("akka-http-circe"))
    implementation(getDependency("postgresql"))
    implementation(getDependency("slick"))
    implementation(getDependency("slick-hikaricp"))
    implementation(getDependency("akka-management"))
    implementation(getDependency("akka-management-cluster-http"))
    implementation(getDependency("akka-management-cluster-bootstrap"))
    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(getDependency("akka-actor-testkit"))
    testImplementation(getDependency("akka-persistence-testkit"))
    testImplementation(getDependency("akka-stream-testkit"))
    testImplementation(getDependency("akka-http-testkit"))
    testImplementation(getDependency("scalatest"))
}

application {
    val extension = otelExtensionArtifact.resolve().first()
    mainClass.set("example.Boot")
    applicationDefaultJvmArgs = listOf(
        "-javaagent:../../opentelemetry-javaagent-1.24.0.jar",
        "-Dotel.javaagent.debug=true",
        "-Dotel.javaagent.extensions=$extension"
    )
}

tasks.named("run") {
    dependsOn(":otel-extension:shadowJar")
}

tasks.withType<JavaExec> {
    standardInput = System.`in`
}
