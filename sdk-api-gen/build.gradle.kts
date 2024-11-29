plugins {
  `java-conventions`
  `test-jar-conventions`
  application
  `library-publishing-conventions`
}

description = "Restate SDK API Gen"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":sdk-api-gen-common"))
  implementation(project(":sdk-api"))

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-core"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.log4j.core)
  testImplementation(libs.jackson.databind)
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(libs.mutiny)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
