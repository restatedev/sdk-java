import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins {
  `java-conventions`
  `kotlin-conventions`
  alias(kotlinLibs.plugins.ksp)
  application
  id("com.github.johnrengelman.shadow").version("8.1.1")
}

dependencies {
  ksp(project(":sdk-api-kotlin-gen"))
  annotationProcessor(project(":sdk-api-gen"))

  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.jsr310)
  implementation(jacksonLibs.jackson.parameter.names)

  implementation(kotlinLibs.kotlinx.coroutines)
  implementation(kotlinLibs.kotlinx.serialization.core)
  implementation(kotlinLibs.kotlinx.serialization.json)

  implementation(coreLibs.log4j.core)
  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
}

application {
  val mainClassValue: String =
      project.findProperty("mainClass")?.toString() ?: "my.restate.sdk.examples.Counter"
  mainClass.set(mainClassValue)
}

tasks.withType<Jar> { this.enabled = false }

tasks.withType<ShadowJar> { transform(ServiceFileTransformer::class.java) }

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
