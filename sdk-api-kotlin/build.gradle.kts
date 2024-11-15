plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  api(project(":sdk-common"))

  implementation(kotlinLibs.kotlinx.coroutines)
  implementation(kotlinLibs.kotlinx.serialization.core)
  implementation(kotlinLibs.kotlinx.serialization.json)

  implementation("io.bkbn:kompendium-json-schema:4.0.0-alpha")

  implementation(coreLibs.log4j.api)
  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.kotlin)

  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.log4j.core)
  testImplementation(coreLibs.protobuf.java)
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")

  testImplementation(project(":sdk-core", "testArchive"))
}
