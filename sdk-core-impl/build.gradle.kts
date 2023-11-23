import com.google.protobuf.gradle.id

plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Implementation of the Restate SDK core"

sourceSets { main { proto { srcDirs("src/main/sdk-proto", "src/main/service-protocol") } } }

dependencies {
  implementation(project(":sdk-core"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.grpc.api)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.log4j.api)

  // We don't want a hard-dependency on it
  compileOnly(coreLibs.log4j.core)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.opentelemetry.semconv)

  testCompileOnly(coreLibs.javax.annotation.api)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
  testImplementation(coreLibs.log4j.core)
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
  }

  generateProtoTasks { ofSourceSet("test").forEach { it.plugins { id("grpc") } } }
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
