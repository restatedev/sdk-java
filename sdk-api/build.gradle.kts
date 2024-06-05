plugins {
  `java-conventions`
  `java-library`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK APIs"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(project(":sdk-common"))

  implementation(coreLibs.log4j.api)

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)

  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.log4j.core)
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
