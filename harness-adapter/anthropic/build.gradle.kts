dependencies {
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.anthropic.java)
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.mapstruct)
    annotationProcessor(libs.mapstruct.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(testFixtures(project(":harness-application")))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock.standalone)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Amapstruct.defaultComponentModel=spring")
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}
