rootProject.name = "mesmer"


dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

include(":core")
include(":otel-extension")
