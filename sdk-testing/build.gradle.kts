plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK testing tools"

dependencies {
  api(project(":sdk-common"))
  api(testingLibs.junit.api)
  api(testingLibs.testcontainers.core)

  implementation(project(":admin-client"))
  implementation(project(":sdk-http-vertx"))
  implementation(coreLibs.log4j.api)
  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)

  testImplementation(project(":sdk-api"))
  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(testingLibs.assertj)
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(coreLibs.log4j.core)
}
