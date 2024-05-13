plugins {
  java
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
