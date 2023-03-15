import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  kotlin("jvm") version "1.6.20" apply false

  id("net.ltgt.errorprone") version "3.0.1"
  id("com.github.jk1.dependency-license-report") version "2.0"
  alias(pluginLibs.plugins.spotless)
  alias(pluginLibs.plugins.protobuf)
}

val protobufVersion = coreLibs.versions.protobuf.get()
val restateVersion = libs.versions.restate.get()

val testReport =
    tasks.register<TestReport>("testReport") {
      destinationDirectory.set(file("$buildDir/reports/tests/test"))
      testResults.setFrom(subprojects.mapNotNull { it.tasks.findByPath("test") })
    }

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "com.github.jk1.dependency-license-report")

  group = "dev.restate"
  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      googleJavaFormat()

      targetExclude("build/generated/**/*.java")
    }
    kotlinGradle { ktfmt() }
  }

  tasks { check { dependsOn(checkLicense) } }

  licenseReport {
    renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())

    excludeBoms = true

    excludes =
        arrayOf(
            "io.vertx:vertx-stack-depchain", // Vertx bom file
            "org.jetbrains.kotlinx:kotlinx-coroutines-core", // Kotlinx coroutines bom file
        )

    allowedLicensesFile = file("$rootDir/config/allowed-licenses.json")
    filters =
        arrayOf(
            com.github.jk1.license.filter.LicenseBundleNormalizer(
                "$rootDir/config/license-normalizer-bundle.json", true))
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "maven-publish")
  apply(plugin = "net.ltgt.errorprone")
  apply(plugin = "com.google.protobuf")

  tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(testReport)
    testLogging {
      events(
          TestLogEvent.PASSED,
          TestLogEvent.SKIPPED,
          TestLogEvent.FAILED,
          TestLogEvent.STANDARD_ERROR,
          TestLogEvent.STANDARD_OUT)
      exceptionFormat = TestExceptionFormat.FULL
    }
  }

  dependencies { errorprone("com.google.errorprone:error_prone_core:2.13.1") }

  protobuf { protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" } }

  java {
    withJavadocJar()
    withSourcesJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
    sourceCompatibility = "11"

    options.errorprone.disableWarningsInGeneratedCode.set(true)
    options.errorprone.excludedPaths.set(".*/build/generated/.*")
  }

  configure<PublishingExtension> {
    repositories {
      maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/restatedev/sdk-java")
        credentials {
          username = System.getenv("GITHUB_ACTOR")
          password = System.getenv("GITHUB_TOKEN")
        }
      }

      maven {
        name = "JFrog"
        val releasesRepoUrl = uri("https://restatedev.jfrog.io/artifactory/restatedev-libs-release")
        val snapshotsRepoUrl =
            uri("https://restatedev.jfrog.io/artifactory/restatedev-libs-snapshot")
        url =
            uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

        credentials {
          username = System.getenv("JFROG_USERNAME")
          password = System.getenv("JFROG_TOKEN")
        }
      }
    }
  }
}
