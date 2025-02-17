import org.gradle.kotlin.dsl.allDependencies
import org.jetbrains.dokka.gradle.AbstractDokkaTask
import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.jsonschema2pojo)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.shadow)

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

  implementation(project(":sdk-common"))

  shade(libs.protobuf.java)
  implementation(libs.log4j.api)

  // We need this for the manifest
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.databind)

  // We don't want a hard-dependency on it
  compileOnly(libs.log4j.core)

  api(libs.opentelemetry.api)

  testCompileOnly(libs.jspecify)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.mutiny)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.log4j.core)
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
      project.configurations["shadow"].allDependencies.forEach {
        exclude(dependency(it))
      }
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
