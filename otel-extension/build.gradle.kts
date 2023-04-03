plugins {
    java
    idea
    scala
    id("com.github.maiflai.scalatest") version "0.32"
}

apply(from = "../dependencies.gradle.kts")
val getDependency = extra["getDependency"] as (name: String) -> String


sourceSets {
    main {
        scala.setSrcDirs(listOf("src/main/scala", "src/main/java"))
        java.setSrcDirs(emptyList<String>())
    }
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
    implementation(getDependency("google-auto-service"))
    implementation(project(":core"))

    testRuntimeOnly(getDependency("flexmark"))
    testImplementation(getDependency("scalatest"))

}
