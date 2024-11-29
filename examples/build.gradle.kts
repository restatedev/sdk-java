import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins {
  `java-conventions`
  `kotlin-conventions`
  alias(libs.plugins.ksp)
  application
  alias(libs.plugins.shadow)
}

dependencies {
  ksp(project(":sdk-api-kotlin-gen"))
  annotationProcessor(project(":sdk-api-gen"))

  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))

  implementation(libs.jackson.jsr310)
  implementation(libs.jackson.parameter.names)

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.log4j.core)
  implementation(libs.vertx.core)
}

application {
  val mainClassValue: String =
      project.findProperty("mainClass")?.toString() ?: "my.restate.sdk.examples.Counter"
  mainClass.set(mainClassValue)
}

tasks.withType<Jar> { this.enabled = false }

tasks.withType<ShadowJar> { transform(ServiceFileTransformer::class.java) }

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
