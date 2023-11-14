import net.ltgt.gradle.errorprone.errorprone
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  `java-library`
  `maven-publish`
  id("org.openapi.generator") version "6.6.0"
}

dependencies {
  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)
  implementation(jacksonLibs.jackson.databind)
  implementation(jacksonLibs.jackson.jsr310)
  implementation("org.openapitools:jackson-databind-nullable:0.2.6")

  // Required for the annotations
  compileOnly(coreLibs.javax.annotation.api)
  compileOnly("com.google.code.findbugs:jsr305:3.0.2")
}

// Add generated output to source sets
sourceSets { main { java.srcDir("$buildDir/generate-resources/main/src/main/java") } }

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

  finalizedBy("spotlessJava")
}

tasks.withType<JavaCompile>().configureEach {
  dependsOn("openApiGenerate")

  targetCompatibility = "11"
  sourceCompatibility = "11"

  // Disable errorprone for this module
  options.errorprone.disableAllChecks.set(true)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  java { targetExclude(fileTree("$buildDir/generate-resources") { include("**/*.java") }) }
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "admin-client"

      from(components["java"])
    }
  }
}
