plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK HTTP implementation based on Vert.x"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  // Vert.x
  implementation(libs.vertx.core)

  // Observability
  implementation(libs.opentelemetry.api)
  implementation(libs.log4j.api)
  implementation(libs.reactiverse.contextual.logging)

  // Testing
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-serde-jackson"))
  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":client"))
  testImplementation(project(":sdk-core", "testArchive"))
  testImplementation(project(":sdk-api", "testArchive"))
  testImplementation(project(":sdk-api-gen", "testArchive"))
  testImplementation(project(":sdk-api-kotlin", "testArchive"))
  testImplementation(project(":sdk-api-kotlin-gen", "testArchive"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.vertx.junit5)
  testImplementation(libs.mutiny)

  testImplementation(libs.protobuf.java)
  testImplementation(libs.protobuf.kotlin)
  testImplementation(libs.log4j.core)

  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.vertx.kotlin.coroutines)
}
