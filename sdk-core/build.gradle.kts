import org.jetbrains.dokka.gradle.AbstractDokkaTask

plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.jsonschema2pojo)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.shadow)
  alias(libs.plugins.ksp)

  // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
  id(libs.plugins.spotless.get().pluginId) apply false
}

description = "Restate SDK Core"

val shade by configurations.creating
val implementation by configurations.getting

implementation.extendsFrom(shade)

val api by configurations.getting

api.extendsFrom(shade)

dependencies {
  compileOnly(libs.jspecify)

  shadow(project(":sdk-common"))

  shade(libs.protobuf.java)
  shadow(libs.log4j.api)

  // We need this for the manifest
  shadow(libs.jackson.annotations)
  shadow(libs.jackson.databind)

  // We don't want a hard-dependency on it
  compileOnly(libs.log4j.core)

  shadow(libs.opentelemetry.api)

  testCompileOnly(libs.jspecify)
  testAnnotationProcessor(project(":sdk-api-gen"))
  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(libs.log4j.api)
  testImplementation(project(":sdk-common"))
  testImplementation(project(":client"))
  testImplementation(project(":client-kotlin"))
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-http-vertx"))
  testImplementation(project(":sdk-lambda"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.opentelemetry.api)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.mutiny)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.log4j.core)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.core)
  testImplementation(libs.vertx.junit5)
  testImplementation(libs.vertx.kotlin.coroutines)
}

// Configure source sets for protobuf plugin and jsonschema2pojo
val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")

sourceSets {
  main {
    java.srcDir(generatedJ2SPDir)
    proto { srcDirs("src/main/service-protocol") }
  }
}

// Configure jsonSchema2Pojo
jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/service-protocol/endpoint_manifest_schema.json"))
  targetPackage = "dev.restate.sdk.core.generated.manifest"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = false
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

// Configure protobuf

val protobufVersion = libs.versions.protobuf.get()

protobuf { protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" } }

// Make sure task dependencies are correct

tasks {
  withType<JavaCompile> { dependsOn(generateJsonSchema2Pojo, generateProto) }
  withType<org.gradle.jvm.tasks.Jar>().configureEach {
    dependsOn(generateJsonSchema2Pojo, generateProto)
  }
  withType<AbstractDokkaTask>().configureEach { dependsOn(generateJsonSchema2Pojo, generateProto) }

  getByName("jar") {
    enabled = false
    dependsOn(shadowJar)
  }

  shadowJar {
    configurations = listOf(shade)
    enableRelocation = true
    archiveClassifier = null
    relocate("com.google.protobuf", "dev.restate.shaded.com.google.protobuf")
    dependencies {
      project.configurations["shadow"].allDependencies.forEach { exclude(dependency(it)) }
      exclude("**/google/protobuf/*.proto")
    }
  }
}

// spotless configuration for protobuf

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  format("proto") {
    target("**/*.proto")

    // Exclude proto and service-protocol directories because those get the license header from
    // their repos.
    targetExclude(
        fileTree("$rootDir/sdk-common/src/main/proto") { include("**/*.*") },
        fileTree("$rootDir/sdk-core/src/main/service-protocol") { include("**/*.*") })

    licenseHeaderFile("$rootDir/config/license-header", "syntax")
  }
}
