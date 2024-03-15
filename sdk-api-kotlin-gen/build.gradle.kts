plugins {
  java
  kotlin("jvm")
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

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
