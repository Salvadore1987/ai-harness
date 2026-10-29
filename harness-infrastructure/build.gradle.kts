plugins {
    `java-library`
}

dependencies {
    api(project(":harness-application"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.ai.starter.model.ollama)
    implementation(libs.spring.ai.starter.mcp.client)
    implementation(libs.picocli)
    implementation(libs.jline)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)
    // Native DNS resolver for the Netty-based WebClient on macOS (the target platform).
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-x86_64")

    testImplementation(testFixtures(project(":harness-application")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock.standalone)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Amapstruct.defaultComponentModel=spring")
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}
