import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  kotlin("jvm") version "1.9.20" apply false

  id("net.ltgt.errorprone") version "3.0.1"
  id("com.github.jk1.dependency-license-report") version "2.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"

  alias(pluginLibs.plugins.spotless)
  alias(pluginLibs.plugins.protobuf)
}

val protobufVersion = coreLibs.versions.protobuf.get()
val restateVersion = libs.versions.restate.get()

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "com.github.jk1.dependency-license-report")

  group = "dev.restate"
  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      targetExclude("build/generated/**/*.java")

      googleJavaFormat()

      licenseHeaderFile("$rootDir/config/license-header")
    }

    format("proto") {
      target("**/*.proto")

      // Exclude proto and service-protocol directories because those get the license header from
      // their repos.
      targetExclude(
          fileTree("$rootDir/sdk-common/src/main/proto") { include("**/*.*") },
          fileTree("$rootDir/sdk-core/src/main/service-protocol") { include("**/*.*") })

      licenseHeaderFile("$rootDir/config/license-header", "syntax")
    }

    kotlin {
      targetExclude("build/generated/**/*.kt")
      ktfmt()
      licenseHeaderFile("$rootDir/config/license-header")
    }

    kotlinGradle { ktfmt() }

    format("properties") {
      target("**/*.properties")
      trimTrailingWhitespace()
    }
  }

  tasks { check { dependsOn(checkLicense) } }

  licenseReport {
    renderers = arrayOf(com.github.jk1.license.render.CsvReportRenderer())

    excludeBoms = true

    excludes =
        arrayOf(
            "io.vertx:vertx-stack-depchain", // Vertx bom file
            "com.google.guava:guava-parent", // Guava bom
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
  apply(plugin = "net.ltgt.errorprone")
  apply(plugin = "com.google.protobuf")

  dependencies { errorprone("com.google.errorprone:error_prone_core:2.13.1") }

  java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
    withJavadocJar()
    withSourcesJar()
  }

  protobuf { protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" } }

  tasks.withType<JavaCompile>().configureEach {
    targetCompatibility = "11"
    sourceCompatibility = "11"

    options.errorprone.disableWarningsInGeneratedCode.set(true)
    options.errorprone.disable(
        // We use toString() in proto messages for debugging reasons.
        "LiteProtoToString",
        // This check is proposing to use a guava API instead...
        "StringSplitter",
        // This is conflicting with a javadoc warn lint
        "MissingSummary")
    options.errorprone.excludedPaths.set(".*/build/generated/.*")
  }

  val testReport =
      tasks.register<TestReport>("testReport") {
        destinationDirectory.set(layout.buildDirectory.dir("reports/tests/test"))
        testResults.setFrom(subprojects.mapNotNull { it.tasks.findByPath("test") })
      }

  // Test platform and reporting
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
}

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
      snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))

      username.set(System.getenv("MAVEN_CENTRAL_USERNAME") ?: return@sonatype)
      password.set(System.getenv("MAVEN_CENTRAL_TOKEN") ?: return@sonatype)
    }
  }
}
