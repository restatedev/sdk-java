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

  api(lambdaLibs.core)
  api(lambdaLibs.events)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)

  implementation(coreLibs.log4j.api)

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-core", "testArchive"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)

  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.protobuf.kotlin)
  testImplementation(coreLibs.log4j.core)

  testImplementation(kotlinLibs.kotlinx.coroutines)
}
