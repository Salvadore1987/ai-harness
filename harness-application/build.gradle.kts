plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(libs.jspecify)
    implementation(libs.java.uuid.generator)
    compileOnlyApi(libs.mapstruct)
    // Only for @Component on MapStruct-generated *MapperImpl classes, see ADR-001 (4).
    compileOnly(libs.spring.context)
    annotationProcessor(libs.mapstruct.processor)

    testFixturesApi(libs.jspecify)
    testFixturesImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.mapstruct)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Amapstruct.defaultComponentModel=spring")
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
