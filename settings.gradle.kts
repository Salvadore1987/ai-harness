rootProject.name = "harness"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("harness-application", "harness-infrastructure", "harness-bootstrap")
