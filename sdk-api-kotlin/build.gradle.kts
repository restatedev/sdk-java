plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  api(project(":sdk-common"))

  implementation(kotlinLibs.kotlinx.coroutines)
  implementation(kotlinLibs.kotlinx.serialization.core)
  implementation(kotlinLibs.kotlinx.serialization.json)

  implementation(coreLibs.log4j.api)
  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.kotlin)

  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.log4j.core)
  testImplementation(coreLibs.protobuf.java)

  testImplementation(project(":sdk-core", "testArchive"))
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
