plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.kotlin)

  testImplementation(project(":sdk-core"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.log4j.core)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.mutiny)

  testImplementation(project(":sdk-core", "testArchive"))
}
