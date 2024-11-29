plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
}

description = "Restate SDK API Kotlin Gen"

dependencies {
  compileOnly(libs.jspecify)

  implementation(libs.ksp.api)
  implementation(project(":sdk-api-gen-common"))

  implementation(project(":sdk-api-kotlin"))

  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(project(":sdk-core"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.log4j.core)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.core)
  testImplementation(libs.mutiny)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
