rootProject.name = "mesmer"


dependencyResolutionManagement {
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

include(":testkit")
include(":core")
include(":otel-extension")
