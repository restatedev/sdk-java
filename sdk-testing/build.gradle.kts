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
  idea
  `maven-publish`
}

sourceSets { main { proto { srcDirs("src/main/sdk-proto", "src/main/service-protocol") } } }

dependencies {
  api(project(":sdk-core"))
  implementation(project(":sdk-core-impl"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.grpc.api)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.log4j.api)
  implementation(coreLibs.log4j.core)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.opentelemetry.semconv)

  implementation(testingLibs.assertj)

  testCompileOnly(coreLibs.javax.annotation.api)

  testImplementation(project(":sdk-core-impl"))
  testImplementation(project(":sdk-blocking"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "sdk-testing"

      from(components["java"])
    }
  }
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
  }

  generateProtoTasks { ofSourceSet("test").forEach { it.plugins { id("grpc") } } }
}
