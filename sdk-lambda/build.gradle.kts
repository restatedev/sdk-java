plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK AWS Lambda integration"

dependencies {
  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  api(libs.aws.lambda.core)
  api(libs.aws.lambda.events)

  implementation(libs.opentelemetry.api)

  implementation(libs.log4j.api)

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-core", "testArchive"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)

  testImplementation(libs.protobuf.java)
  testImplementation(libs.protobuf.kotlin)
  testImplementation(libs.log4j.core)

  testImplementation(libs.kotlinx.coroutines.core)
}
