import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-conventions`
  `kotlin-conventions`
  application
  alias(libs.plugins.shadow)
}

dependencies {
  implementation(project(":client"))
  implementation(project(":client-kotlin"))
  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))

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

tasks.withType<ShadowJar> { mergeServiceFiles() }

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
