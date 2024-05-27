plugins {
  `java-library`
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
