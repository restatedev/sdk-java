import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  id("com.google.devtools.ksp") version "1.9.22-1.0.17"
  application
  id("com.github.johnrengelman.shadow").version("8.1.1")
}

dependencies {
  annotationProcessor(project(":sdk-api-gen"))
  ksp(project(":sdk-api-kotlin-gen"))

  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))
  implementation(project(":sdk-workflow-api"))

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.jsr310)

  implementation(kotlinLibs.kotlinx.coroutines)
  implementation(kotlinLibs.kotlinx.serialization.core)
  implementation(kotlinLibs.kotlinx.serialization.json)

  implementation(coreLibs.log4j.core)
}

application {
  val mainClassValue: String =
      project.findProperty("mainClass")?.toString() ?: "my.restate.sdk.examples.Counter"
  mainClass.set(mainClassValue)
}

tasks.withType<Jar> {
  this.metaInf.duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
tasks.withType<ShadowJar> { transform(ServiceFileTransformer::class.java) }
