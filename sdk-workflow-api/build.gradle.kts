import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf

plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Workflow APIs"

dependencies {
  api(project(":sdk-common"))
  api(project(":sdk-api"))

  // For the gRPC Ingress client
  protobuf(project(":sdk-common"))

  compileOnly(coreLibs.javax.annotation.api)
  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.protobuf.util)
  implementation(coreLibs.grpc.stub)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.log4j.core)
  implementation(project(":sdk-serde-jackson"))

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)

  // Import test suites from sdk-core
  testImplementation(project(":sdk-core", "testArchive"))
  testProtobuf(project(":sdk-core", "testArchive"))
  testCompileOnly(coreLibs.javax.annotation.api)
}

val pluginJar =
    file(
        "${project.rootProject.rootDir}/protoc-gen-restate/build/libs/protoc-gen-restate-${project.version}-all.jar")

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
    id("restate") {
      // NOTE: This is not needed in a regular project configuration, you should rather use:
      // artifact = "dev.restate.sdk:protoc-gen-restate-java-blocking:1.0-SNAPSHOT:all@jar"
      path = pluginJar.path
    }
  }

  generateProtoTasks {
    all().forEach {
      // Make sure we depend on shadowJar from protoc-gen-restate
      it.dependsOn(":protoc-gen-restate:shadowJar")

      it.plugins {
        id("grpc")
        id("restate")
      }
    }
  }
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
