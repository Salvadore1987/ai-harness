// LLM provider adapters: each one implements the ports of the core and knows nothing else.
subprojects {
    apply(plugin = "java-library")

    configure<BasePluginExtension> {
        archivesName.set("harness-adapter-${project.name}")
    }

    dependencies {
        "api"(project(":harness-application"))
    }
}
