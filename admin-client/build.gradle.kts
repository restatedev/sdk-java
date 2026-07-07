import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
  `java-library`
  `java-conventions`
  alias(libs.plugins.openapi.generator)
  `library-publishing-conventions`
}

description = "Code-generated Admin API client for Restate"

dependencies {
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.jsr310)

  // Required for the annotations
  compileOnly(libs.tomcat.annotations)
  compileOnly(libs.google.findbugs.jsr305)
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

  // This whole client is deprecated in favour of dev.restate.client.Client. We deprecate every
  // generated type:
  // * The custom generatedAnnotation.mustache template (see openapi-templates) adds @Deprecated to
  //   all the api/model/invoker classes, right after the @Generated annotation.
  // * additionalEnumTypeAnnotations covers the standalone enums, which don't carry @Generated.
  templateDir.set("$projectDir/openapi-templates")
  configOptions.put("additionalEnumTypeAnnotations", "@Deprecated")

  finalizedBy("spotlessJava")
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  java { targetExclude(fileTree("build/generate-resources") { include("**/*.java") }) }
}
