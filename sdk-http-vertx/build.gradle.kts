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
  `java-library`
  kotlin("jvm")
  idea
  `maven-publish`
}

dependencies {
  api(project(":sdk-core"))
  implementation(project(":sdk-core-impl"))

  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(vertxLibs.vertx.grpc.context.storage)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.log4j.api)
  implementation("io.reactiverse:reactiverse-contextual-logging:1.1.2")

  testImplementation(project(":sdk-java-blocking"))
  testImplementation(project(":sdk-kotlin"))
  testImplementation(project(":sdk-core-impl", "testArchive"))
  testImplementation(project(":sdk-java-blocking", "testArchive"))
  testImplementation(project(":sdk-kotlin", "testArchive"))
  testProtobuf(project(":sdk-core-impl", "testArchive"))
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
    ofSourceSet("test").forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }
    }
  }
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "sdk-http-vertx"

      from(components["java"])
    }
  }
}
