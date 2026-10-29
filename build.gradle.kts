import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotbugs) apply false
}

val catalog = libs
val liveTags: String? = providers.gradleProperty("includeTags").orNull

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "pmd")
    apply(plugin = "jacoco")
    apply(plugin = "net.ltgt.errorprone")
    apply(plugin = "com.github.spotbugs")

    group = "dev.localllm.harness"
    version = "0.1.0-SNAPSHOT"

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(catalog.versions.jdk.get()))
        }
    }

    configurations.all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
        exclude(group = "ch.qos.logback")
    }

    dependencies {
        val bom = platform(catalog.spring.boot.bom)
        val aiBom = platform(catalog.spring.ai.bom)
        "implementation"(bom)
        "implementation"(aiBom)
        "compileOnly"(bom)
        "annotationProcessor"(bom)
        "testImplementation"(bom)
        "implementation"(catalog.jspecify)
        "errorprone"(catalog.errorprone.core)
        "errorprone"(catalog.nullaway)
        "testImplementation"(catalog.junit.jupiter)
        "testImplementation"(catalog.assertj.core)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial"))
        options.errorprone {
            disableWarningsInGeneratedCode.set(true)
            excludedPaths.set(".*/build/generated/.*")
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "dev.localllm")
        }
    }

    tasks.named<JavaCompile>("compileTestJava") {
        options.errorprone.enabled.set(false)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            if (liveTags != null) {
                includeTags(liveTags)
            } else {
                excludeTags("live", "native")
            }
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading")
        finalizedBy("jacocoTestReport")
    }

    configure<CheckstyleExtension> {
        toolVersion = catalog.versions.checkstyle.get()
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        maxWarnings = 0
    }

    configure<PmdExtension> {
        toolVersion = catalog.versions.pmd.get()
        ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
        ruleSets = emptyList()
        isConsoleOutput = true
    }

    tasks.withType<Pmd>().configureEach {
        exclude("**/*MapperImpl.java")
    }

    configure<SpotBugsExtension> {
        toolVersion.set(catalog.versions.spotbugs.tool.get())
        reportLevel.set(Confidence.HIGH)
        excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
    }

    tasks.withType<SpotBugsTask>().configureEach {
        reports.create("html") { required.set(true) }
    }

    configure<JacocoPluginExtension> {
        toolVersion = catalog.versions.jacoco.get()
    }

    tasks.withType<JacocoReportBase>().configureEach {
        classDirectories.setFrom(
            files(classDirectories.files.map {
                fileTree(it) { exclude("**/*MapperImpl.class") }
            })
        )
    }

    tasks.named("check") {
        dependsOn("jacocoTestCoverageVerification")
    }
}
