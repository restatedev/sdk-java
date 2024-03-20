plugins {
  `java-library`
  `library-publishing-conventions`
  alias(pluginLibs.plugins.protobuf)
}

description = "Restate SDK Workflow APIs"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(project(":sdk-common"))
  api(project(":sdk-api"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.log4j.core)

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.annotations)
  implementation(jacksonLibs.jackson.jsr310)
  implementation(project(":sdk-serde-jackson"))
  implementation(project(":sdk-serde-protobuf"))

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
}

// Configure protobuf

val protobufVersion = coreLibs.versions.protobuf.get()

protobuf { protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" } }

// Make sure task dependencies are correct

tasks {
  withType<JavaCompile> { dependsOn(generateProto) }
  withType<Jar> { dependsOn(generateProto) }
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
