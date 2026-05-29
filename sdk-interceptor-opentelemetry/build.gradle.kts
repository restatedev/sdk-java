plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK OpenTelemetry tracing interceptor (Java + Kotlin)"

dependencies {
  compileOnly(libs.jspecify)

  // Both API flavors are compile-only. Consumers bring in whichever they're using.
  compileOnly(project(":sdk-api"))
  compileOnly(project(":sdk-api-kotlin"))

  // Used by both Java and Kotlin classes.
  api(libs.opentelemetry.api)

  // Kotlin-only runtime deps. Kotlin consumers get them transitively via sdk-api-kotlin.
  compileOnly(libs.opentelemetry.kotlin)
  compileOnly(libs.kotlinx.coroutines.core)

  implementation(libs.log4j.api)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.kotlinx.coroutines.test)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// Apply Java spotless rules in addition to the Kotlin rules from kotlin-conventions.
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  java {
    targetExclude("build/**/*.java")
    googleJavaFormat()
    licenseHeaderFile("$rootDir/config/license-header")
  }
}
