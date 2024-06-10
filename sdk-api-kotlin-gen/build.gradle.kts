plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
  alias(kotlinLibs.plugins.ksp)
}

description = "Restate SDK API Kotlin Gen"

dependencies {
  compileOnly(coreLibs.jspecify)

  implementation(kotlinLibs.symbol.processing.api)
  implementation(project(":sdk-api-gen-common"))

  implementation(project(":sdk-api-kotlin"))

  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.log4j.core)
  testImplementation(kotlinLibs.kotlinx.coroutines)
  testImplementation(kotlinLibs.kotlinx.serialization.core)
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}
