import net.ltgt.gradle.errorprone.errorprone
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  `java-library`
  `java-conventions`
  id("org.openapi.generator") version "7.5.0"
  `library-publishing-conventions`
}

description = "Code-generated Admin API client for Restate"

dependencies {
  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)
  implementation(jacksonLibs.jackson.databind)
  implementation(jacksonLibs.jackson.jsr310)

  // Required for the annotations
  compileOnly("org.apache.tomcat:annotations-api:6.0.53")
  compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

// Add generated output to source sets
sourceSets { main { java.srcDir(tasks.named("openApiGenerate")) } }

// Configure openapi generator
tasks.withType<GenerateTask> {
  inputSpec.set("$projectDir/src/main/openapi/meta.json")

  // Java 9+ HTTP Client using Jackson
  generatorName.set("java")
  library.set("native")

  // Package names
  invokerPackage.set("dev.restate.admin.client")
  apiPackage.set("dev.restate.admin.api")
  modelPackage.set("dev.restate.admin.model")

  // We don't need these
  generateApiTests.set(false)
  generateApiDocumentation.set(false)
  generateModelTests.set(false)
  generateModelDocumentation.set(false)

  configOptions.put("openApiNullable", "false")

  finalizedBy("spotlessJava")
}

tasks.withType<JavaCompile>().configureEach {
  // Disable errorprone for this module
  options.errorprone.disableAllChecks.set(true)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  java { targetExclude(fileTree("build/generate-resources") { include("**/*.java") }) }
}
