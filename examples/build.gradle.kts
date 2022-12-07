import com.google.protobuf.gradle.id

// Without these suppressions version catalog usage here and in other build
// files is marked red by IntelliJ:
// https://youtrack.jetbrains.com/issue/KTIJ-19369.
@Suppress(
    "DSL_SCOPE_VIOLATION",
    "MISSING_DEPENDENCY_CLASS",
    "UNRESOLVED_REFERENCE_WRONG_RECEIVER",
    "FUNCTION_CALL_EXPECTED")
plugins {
  java
  kotlin("jvm") version "1.6.20"
  idea
  `maven-publish`
}

dependencies {
  implementation(project(":sdk-blocking"))
  implementation(project(":sdk-vertx"))
  implementation(project(":sdk-kotlin"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.protobuf.kotlin)
  implementation(coreLibs.grpc.stub)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.grpc.kotlin.stub)

  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(vertxLibs.vertx.kotlin.coroutines)

  implementation(coreLibs.log4j.core)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    ktfmt()
    targetExclude("build/generated/**/*.kt")
  }
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
    id("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${coreLibs.versions.grpckt.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }
    }
  }
}
