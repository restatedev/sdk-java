/*
 * This file was generated by the Gradle 'init' task.
 *
 * The settings file is used to specify which projects to include in your build.
 *
 * Detailed information about configuring a multi-project build in Gradle can be found
 * in the user manual at https://docs.gradle.org/7.4.2/userguide/multi_project_builds.html
 */

rootProject.name = "sdk-java"

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0" }

include(
    "admin-client",
    "common",
    "client",
    "client-kotlin",
    "sdk-common",
    "sdk-api",
    "sdk-api-kotlin",
    "sdk-core",
    "sdk-serde-jackson",
    "sdk-request-identity",
    "sdk-http-vertx",
    "sdk-lambda",
    "sdk-testing",
    "sdk-api-gen-common",
    "sdk-api-gen",
    "sdk-api-kotlin-gen",
    "sdk-spring-boot",

    // Other modules we don't publish
    "examples",
    "sdk-aggregated-javadocs",
    "test-services",

    // Meta modules
    "sdk-java-http",
    "sdk-java-lambda",
    "sdk-kotlin-http",
    "sdk-kotlin-lambda",
    "sdk-spring-boot-starter",
    "sdk-spring-boot-kotlin-starter",
)

dependencyResolutionManagement { repositories { mavenCentral() } }
