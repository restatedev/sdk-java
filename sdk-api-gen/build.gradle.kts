plugins {
  `java-conventions`
  `test-jar-conventions`
  application
  `library-publishing-conventions`
}

description = "Restate SDK API Gen"

dependencies {
  compileOnly(coreLibs.jspecify)

  implementation(project(":sdk-api-gen-common"))

  implementation(project(":sdk-api"))

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.log4j.core)
  testImplementation(platform(jacksonLibs.jackson.bom))
  testImplementation(jacksonLibs.jackson.databind)
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
