import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    alias(libs.plugins.spotless)
}

val restateVersion = libs.versions.restate.get()

val testReport = tasks.register<TestReport>("testReport") {
    destinationDirectory.set(file("$buildDir/reports/tests/test"))
    testResults.setFrom(subprojects.mapNotNull {
        it.tasks.findByPath("test")
    })
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    version = restateVersion

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(testReport)
        testLogging {
            events(
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED,
                TestLogEvent.FAILED,
                TestLogEvent.STANDARD_ERROR,
                TestLogEvent.STANDARD_OUT
            )
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        targetCompatibility = "11"
        sourceCompatibility = "11"
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat()

            targetExclude("build/generated/**/*.java")
        }
    }
}