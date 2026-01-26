import com.github.jk1.license.render.ReportRenderer

plugins {
  alias(libs.plugins.dependency.license.report)
  alias(libs.plugins.nexus.publish)
  alias(libs.plugins.dokka)

  // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
  id(libs.plugins.spotless.get().pluginId) apply false
}

// Dokka is bringing in jackson unshaded, and it's messing up other plugins, so we override those
// here!
buildscript {
  dependencies {
    classpath("com.fasterxml.jackson.core:jackson-core:2.17.1")
    classpath("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.17.1")
    classpath("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    classpath("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.9.20") {
      exclude("com.fasterxml.jackson")
      exclude("com.fasterxml.jackson.dataformat")
      exclude("com.fasterxml.jackson.module")
    }
  }
}

val restateVersion = libs.versions.restate.get()

allprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "com.github.jk1.dependency-license-report")

  group = "dev.restate"
  version = restateVersion

  configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlinGradle { ktfmt() }

    format("properties") {
      target("**/*.properties")
      trimTrailingWhitespace()
    }
  }

  tasks.named("check") { dependsOn("checkLicense") }

  licenseReport {
    renderers = arrayOf<ReportRenderer>(com.github.jk1.license.render.CsvReportRenderer())

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
                "$rootDir/config/license-normalizer-bundle.json",
                true,
            )
        )
  }
}

// Dokka configuration
subprojects
    .filter {
      !setOf(
              "sdk-api",
              "sdk-api-gen",
              "sdk-fake-api",
              "examples",
              "sdk-aggregated-javadocs",
              "admin-client",
              "test-services",
          )
          .contains(it.name)
    }
    .forEach { p -> p.plugins.apply("org.jetbrains.dokka") }

nexusPublishing {
  repositories {
    sonatype {
      nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
      snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))

      username.set(System.getenv("MAVEN_CENTRAL_USERNAME") ?: return@sonatype)
      password.set(System.getenv("MAVEN_CENTRAL_TOKEN") ?: return@sonatype)
    }
  }
}
