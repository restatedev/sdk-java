plugins {
  java
  application
  `library-publishing-conventions`
}

description = "Restate SDK API Gen"

dependencies {
  implementation(project(":sdk-common"))
  implementation(project(":sdk-api"))
  implementation(project(":sdk-workflow-api"))
  implementation(project(":sdk-serde-jackson"))

  implementation("com.github.jknack:handlebars:4.3.1")

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-core"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.log4j.core)
  testCompileOnly(coreLibs.javax.annotation.api)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
  testProtobuf(project(":sdk-core", "testArchive"))
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
