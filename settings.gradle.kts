/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/7.4.2/userguide/multi_project_builds.html
 */

rootProject.name = "sdk-java"

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0" }

include(
    "admin-client",
    "sdk-common",
    "sdk-api",
    "sdk-api-kotlin",
    "sdk-core",
    "sdk-serde-jackson",
    "sdk-serde-protobuf",
    "sdk-request-identity",
    "sdk-http-vertx",
    "sdk-lambda",
    "sdk-testing",
    "sdk-api-gen-common",
    "sdk-api-gen",
    "sdk-api-kotlin-gen",
    "sdk-spring-boot-starter",
    "examples",
    "sdk-aggregated-javadocs",
    "test-services")

dependencyResolutionManagement {
  repositories { mavenCentral() }

  versionCatalogs {
    create("coreLibs") {
      version("protobuf", "4.28.3")
      version("log4j", "2.24.1")
      version("opentelemetry", "1.44.1")

      library("protoc", "com.google.protobuf", "protoc").versionRef("protobuf")
      library("protobuf-java", "com.google.protobuf", "protobuf-java").versionRef("protobuf")
      library("protobuf-kotlin", "com.google.protobuf", "protobuf-kotlin").versionRef("protobuf")

      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j")

      library("opentelemetry-bom", "io.opentelemetry", "opentelemetry-bom")
          .versionRef("opentelemetry")
      library("opentelemetry-api", "io.opentelemetry", "opentelemetry-api").withoutVersion()
      library("opentelemetry-kotlin", "io.opentelemetry", "opentelemetry-extension-kotlin")
          .withoutVersion()

      library("jspecify", "org.jspecify", "jspecify").version("1.0.0")

      library("jwt", "com.nimbusds:nimbus-jose-jwt:9.47")
      library("tink", "com.google.crypto.tink:tink:1.15.0")
    }
    create("vertxLibs") {
      library("vertx-bom", "io.vertx:vertx-stack-depchain:4.5.11")
      library("vertx-core", "io.vertx", "vertx-core").withoutVersion()
      library("vertx-kotlin-coroutines", "io.vertx", "vertx-lang-kotlin-coroutines")
          .withoutVersion()
      library("vertx-junit5", "io.vertx", "vertx-junit5").withoutVersion()
    }
    create("lambdaLibs") {
      library("core", "com.amazonaws:aws-lambda-java-core:1.2.3")
      library("events", "com.amazonaws:aws-lambda-java-events:3.11.5")
    }
    create("jacksonLibs") {
      version("jackson", "2.18.1")

      library("jackson-bom", "com.fasterxml.jackson", "jackson-bom").versionRef("jackson")
      library("jackson-annotations", "com.fasterxml.jackson.core", "jackson-annotations")
          .withoutVersion()
      library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").withoutVersion()
      library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").withoutVersion()
      library("jackson-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310")
          .withoutVersion()
      library("jackson-jdk8", "com.fasterxml.jackson.datatype", "jackson-datatype-jdk8")
          .withoutVersion()
      library(
              "jackson-parameter-names",
              "com.fasterxml.jackson.module",
              "jackson-module-parameter-names")
          .withoutVersion()
    }
    create("kotlinLibs") {
      library("kotlinx-coroutines", "org.jetbrains.kotlinx", "kotlinx-coroutines-core")
          .version("1.9.0")
      library("kotlinx-serialization-core", "org.jetbrains.kotlinx", "kotlinx-serialization-core")
          .version("1.7.3")
      library("kotlinx-serialization-json", "org.jetbrains.kotlinx", "kotlinx-serialization-json")
          .version("1.7.3")

      version("ksp", "2.0.21-1.0.28")
      library("symbol-processing-api", "com.google.devtools.ksp", "symbol-processing-api")
          .versionRef("ksp")
      plugin("ksp", "com.google.devtools.ksp").versionRef("ksp")
    }
    create("testingLibs") {
      version("junit-jupiter", "5.10.2")
      version("assertj", "3.26.0")
      version("testcontainers", "1.20.3")

      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit-jupiter")
      library("junit-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit-jupiter")

      library("assertj", "org.assertj", "assertj-core").versionRef("assertj")

      library("testcontainers-core", "org.testcontainers", "testcontainers")
          .versionRef("testcontainers")
      library("testcontainers-toxiproxy", "org.testcontainers", "toxiproxy")
          .versionRef("testcontainers")
    }
    create("pluginLibs") {
      plugin("spotless", "com.diffplug.spotless").version("6.25.0")
      plugin("protobuf", "com.google.protobuf").version("0.9.4")
      plugin("test-logger", "com.adarshr.test-logger").version("4.0.0")
    }
  }
}
