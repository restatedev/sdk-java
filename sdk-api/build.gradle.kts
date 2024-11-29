plugins {
  `java-conventions`
  `java-library`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK APIs"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-common"))

  implementation(libs.log4j.api)

  implementation(libs.jackson.core)

  testImplementation(project(":sdk-core"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.log4j.core)
  testImplementation(libs.mutiny)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
