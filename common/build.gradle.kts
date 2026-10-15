plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Common types used by different Restate Java modules"

dependencies {
  compileOnly(libs.jspecify)
  compileOnly(libs.jetbrains.annotations)

  implementation(libs.log4j.api)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Javadoc> { isFailOnError = false }
