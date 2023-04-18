plugins {
    idea
    scala
    application
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
    implementation(getDependency("akka-actor-typed"))
    implementation(getDependency("akka-actor"))
    implementation(getDependency("akka-serialization-jackson"))
    implementation(getDependency("akka-cluster-sharding-typed"))
}

application {
    val extension = otelExtensionArtifact.resolve().first()
    mainClass.set("example.SimpleStreamExample")
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
