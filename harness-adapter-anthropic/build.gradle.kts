plugins {
    `java-library`
}

dependencies {
    api(project(":harness-application"))

    implementation(libs.anthropic.java)
}
