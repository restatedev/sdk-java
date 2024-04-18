import net.ltgt.gradle.errorprone.errorprone

plugins {
  java
  kotlin("jvm") version "1.9.22" apply false
  kotlin("plugin.serialization") version "1.9.22" apply false

  id("net.ltgt.errorprone") version "3.0.1"
  id("com.github.jk1.dependency-license-report") version "2.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"

  alias(pluginLibs.plugins.spotless)
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
      targetExclude("build/**/*.java")

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
            // kotlinx dependencies are APL 2, but somehow the plugin doesn't recognize that.
            "org.jetbrains.kotlinx:kotlinx-coroutines-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-core",
            "org.jetbrains.kotlinx:kotlinx-serialization-json",
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

  dependencies { errorprone("com.google.errorprone:error_prone_core:2.13.1") }

  // Configure the java toolchain to use. If not found, it will be downloaded automatically
  java {
    toolchain { languageVersion = JavaLanguageVersion.of(11) }

    withJavadocJar()
    withSourcesJar()
  }

  tasks.withType<JavaCompile>().configureEach {
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

  tasks.withType<Test> { useJUnitPlatform() }
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
