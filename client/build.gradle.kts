plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate Client to interact with services from within other Java applications"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":common"))

  implementation(libs.jackson.core)
  implementation(libs.log4j.api)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
}
