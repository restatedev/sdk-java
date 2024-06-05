plugins {
  id("com.github.jk1.dependency-license-report") version "2.0"
  id("io.github.gradle-nexus.publish-plugin") version "1.3.0"

  // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
  id(pluginLibs.plugins.spotless.get().pluginId) apply false
}

val protobufVersion = coreLibs.versions.protobuf.get()
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
