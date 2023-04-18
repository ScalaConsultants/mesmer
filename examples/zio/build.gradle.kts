plugins {
    idea
    scala
    application
}

apply(from = "../../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String

tasks.withType<JavaExec>() {
    standardInput = System.`in`
}

val otelExtensionArtifact by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true

}
dependencies {
    otelExtensionArtifact(project(path = ":otel-extension", configuration = "extensionArtifact"))
    implementation(getDependency("zio-core"))
    implementation(getDependency("logback"))
}


application {
    val extension = otelExtensionArtifact.resolve().first()
    mainClass.set("example.SimpleZioExample")
    applicationDefaultJvmArgs = listOf(
        "-javaagent:../../opentelemetry-javaagent-1.24.0.jar",
        "-Dotel.javaagent.debug=true",
        "-Dotel.javaagent.extensions=$extension"
    )
}

tasks.named("run") {
    dependsOn(":otel-extension:shadowJar")
}

tasks.withType<JavaExec>() {
    standardInput = System.`in`
}
