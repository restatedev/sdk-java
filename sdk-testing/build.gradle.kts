plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK testing tools"

dependencies {
  api(project(":sdk-common"))
  api(libs.junit.api)
  api(libs.testcontainers)

  implementation(project(":admin-client"))
  implementation(project(":sdk-http-vertx"))
  implementation(libs.log4j.api)
  implementation(libs.vertx.core)

  testImplementation(project(":sdk-api"))
  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(libs.assertj)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.log4j.core)
}
