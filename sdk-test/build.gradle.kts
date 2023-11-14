import com.google.protobuf.gradle.id

plugins {
  `java-library`
  `maven-publish`
}

dependencies {
  api(project(":sdk-core"))
  api(testingLibs.junit.api)
  api(testingLibs.testcontainers.core)

  implementation(project(":admin-client"))
  implementation(project(":sdk-http-vertx"))
  implementation(coreLibs.log4j.api)
  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(coreLibs.grpc.netty.shaded)

  testCompileOnly(coreLibs.javax.annotation.api)
  testImplementation(project(":sdk-java-blocking"))
  testImplementation(testingLibs.assertj)
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
  testImplementation(coreLibs.log4j.core)
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "sdk-test"

      from(components["java"])
    }
  }
}

// Protobuf codegen for tests

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
  }

  generateProtoTasks { ofSourceSet("test").forEach { it.plugins { id("grpc") } } }
}
