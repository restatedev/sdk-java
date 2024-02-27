import com.google.protobuf.gradle.id

plugins {
  `java-library`
  kotlin("jvm")
  `library-publishing-conventions`
}

description = "Restate SDK HTTP implementation based on Vert.x"

dependencies {
  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(vertxLibs.vertx.grpc.context.storage)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.log4j.api)
  implementation("io.reactiverse:reactiverse-contextual-logging:1.1.2")

  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-core", "testArchive"))
  testImplementation(project(":sdk-api", "testArchive"))
  testImplementation(project(":sdk-api-kotlin", "testArchive"))
  testProtobuf(project(":sdk-core", "testArchive"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(vertxLibs.vertx.junit5)

  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.protobuf.kotlin)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
  testImplementation(coreLibs.grpc.kotlin.stub)
  testImplementation(coreLibs.log4j.core)

  testImplementation(kotlinLibs.kotlinx.coroutines)
  testImplementation(vertxLibs.vertx.kotlin.coroutines)
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
