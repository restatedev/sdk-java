import org.jetbrains.dokka.gradle.AbstractDokkaTask

plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
  id("org.jsonschema2pojo") version "1.2.1"
  alias(pluginLibs.plugins.protobuf)

  // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
  id(pluginLibs.plugins.spotless.get().pluginId) apply false
}

description = "Restate SDK Core"

dependencies {
  compileOnly(coreLibs.jspecify)

  implementation(project(":sdk-common"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.log4j.api)

  // We need this for the manifest
  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.annotations)
  implementation(jacksonLibs.jackson.databind)

  // We don't want a hard-dependency on it
  compileOnly(coreLibs.log4j.core)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)

  testCompileOnly(coreLibs.jspecify)
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.log4j.core)
}

// Configure source sets for protobuf plugin and jsonschema2pojo
val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")

sourceSets {
  main {
    java.srcDir(generatedJ2SPDir)
    proto { srcDirs("src/main/sdk-proto", "src/main/service-protocol") }
  }
}

// Configure jsonSchema2Pojo
jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/service-protocol/endpoint_manifest_schema.json"))
  targetPackage = "dev.restate.sdk.core.manifest"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = false
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

// Configure protobuf

val protobufVersion = coreLibs.versions.protobuf.get()

protobuf { protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" } }

// Make sure task dependencies are correct

tasks {
  withType<JavaCompile> { dependsOn(generateJsonSchema2Pojo, generateProto) }
  withType<Jar> { dependsOn(generateJsonSchema2Pojo, generateProto) }
  withType<AbstractDokkaTask>().configureEach { dependsOn(generateJsonSchema2Pojo, generateProto) }
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
