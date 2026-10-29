plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":harness-infrastructure"))
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.log4j2)
    // Constraint annotations of HarnessProperties records read while compiling BeanConfig.
    implementation(libs.spring.boot.starter.validation)

    testImplementation(testFixtures(project(":harness-application")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.picocli)
    testImplementation(libs.mapstruct)
    testImplementation(libs.wiremock.standalone)
}

tasks.bootJar {
    archiveFileName.set("harness.jar")
}

tasks.jar {
    enabled = false
}

val coveredProjects = listOf(
    project(":harness-application"),
    project(":harness-infrastructure"),
    project
)

val overallCoverageVerification by tasks.registering(JacocoCoverageVerification::class) {
    description = "Verifies the overall line coverage threshold (60 %) across all modules."
    coveredProjects.forEach { dependsOn(it.tasks.named("test")) }
    executionData.setFrom(coveredProjects.map { fileTree(it.layout.buildDirectory) { include("jacoco/*.exec") } })
    sourceDirectories.setFrom(coveredProjects.map { it.file("src/main/java") })
    classDirectories.setFrom(coveredProjects.map {
        fileTree(it.layout.buildDirectory.dir("classes/java/main")) { exclude("**/*MapperImpl.class") }
    })
    violationRules {
        rule {
            limit {
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.jacocoTestCoverageVerification {
    enabled = false
}

tasks.check {
    dependsOn(overallCoverageVerification)
}
