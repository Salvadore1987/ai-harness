rootProject.name = "harness"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "harness-application",
    "harness-infrastructure",
    "harness-adapter",
    "harness-adapter:ollama",
    "harness-adapter:anthropic",
    "harness-bootstrap"
)
