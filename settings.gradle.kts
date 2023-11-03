/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/7.4.2/userguide/multi_project_builds.html
 */

rootProject.name = "sdk-java"

include(
    "sdk-core",
    "sdk-core-impl",
    "sdk-serde-jackson",
    "sdk-http-vertx",
    "sdk-java-blocking",
    "sdk-lambda",
    "sdk-kotlin",
    "sdk-testing",
    "examples:http",
    "examples:lambda")

dependencyResolutionManagement {
  repositories { mavenCentral() }

  versionCatalogs {
    create("coreLibs") {
      version("protobuf", "3.24.3")
      version("grpc", "1.58.0")
      version("grpckt", "1.4.0")
      version("log4j", "2.20.0")
      version("opentelemetry", "1.30.1")

      library("protoc", "com.google.protobuf", "protoc").versionRef("protobuf")
      library("protobuf-java", "com.google.protobuf", "protobuf-java").versionRef("protobuf")
      library("protobuf-kotlin", "com.google.protobuf", "protobuf-kotlin").versionRef("protobuf")

      library("grpc-core", "io.grpc", "grpc-core").versionRef("grpc")
      library("grpc-stub", "io.grpc", "grpc-stub").versionRef("grpc")
      library("grpc-protobuf", "io.grpc", "grpc-protobuf").versionRef("grpc")
      library("grpc-netty-shaded", "io.grpc", "grpc-netty-shaded").versionRef("grpc")
      library("grpc-api", "io.grpc", "grpc-api").versionRef("grpc")
      library("grpc-kotlin-stub", "io.grpc", "grpc-kotlin-stub").versionRef("grpckt")

      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")

      library("opentelemetry-bom", "io.opentelemetry", "opentelemetry-bom")
          .versionRef("opentelemetry")
      library("opentelemetry-api", "io.opentelemetry", "opentelemetry-api").withoutVersion()
      library("opentelemetry-semconv", "io.opentelemetry:opentelemetry-semconv:1.19.0-alpha")

      library("javax-annotation-api", "org.apache.tomcat", "annotations-api").version("6.0.53")
    }
    create("vertxLibs") {
      library("vertx-bom", "io.vertx:vertx-stack-depchain:4.4.6")
      library("vertx-core", "io.vertx", "vertx-core").withoutVersion()
      library("vertx-grpc-context-storage", "io.vertx", "vertx-grpc-context-storage")
          .withoutVersion()
      library("vertx-kotlin-coroutines", "io.vertx", "vertx-lang-kotlin-coroutines")
          .withoutVersion()
      library("vertx-junit5", "io.vertx", "vertx-junit5").withoutVersion()
    }
    create("lambdaLibs") {
      library("core", "com.amazonaws:aws-lambda-java-core:1.2.2")
      library("events", "com.amazonaws:aws-lambda-java-events:3.11.0")
    }
    create("jacksonLibs") {
      version("jackson", "2.15.2")

      library("jackson-bom", "com.fasterxml.jackson", "jackson-bom").versionRef("jackson")
      library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").withoutVersion()
      library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind")
          .versionRef("jackson")
    }
    create("kotlinLibs") {
      library("kotlinx-coroutines", "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
          .version("1.7.3")
    }
    create("testingLibs") {
      version("junit-jupiter", "5.9.1")
      version("assertj", "3.23.1")

      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit-jupiter")
      library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit-jupiter")

      library("assertj", "org.assertj", "assertj-core").versionRef("assertj")
    }
    create("pluginLibs") {
      plugin("spotless", "com.diffplug.spotless").version("6.22.0")
      plugin("protobuf", "com.google.protobuf").version("0.9.4")
      plugin("test-logger", "com.adarshr.test-logger").version("4.0.0")
    }
  }
}
