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
include(":examples:akka")
include(":examples:akka-stream")
