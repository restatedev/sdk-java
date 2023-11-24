import com.google.protobuf.gradle.id

plugins {
  java
  kotlin("jvm")
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin interface"

dependencies {
  api(project(":sdk-core"))

  implementation(kotlinLibs.kotlinx.coroutines)

  testImplementation(project(":sdk-core-impl"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.protobuf.kotlin)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
  testImplementation(coreLibs.grpc.kotlin.stub)
  testImplementation(coreLibs.log4j.core)

  testImplementation(project(":sdk-core-impl", "testArchive"))
  testProtobuf(project(":sdk-core-impl", "testArchive"))
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
    id("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${coreLibs.versions.grpckt.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    ofSourceSet("test").forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }
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
