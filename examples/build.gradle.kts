plugins {
  java
  kotlin("jvm")
  kotlin("plugin.serialization")
  application
  id("com.github.johnrengelman.shadow").version("7.1.2")
}

dependencies {
  annotationProcessor(project(":sdk-api-gen"))

  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))
  implementation(project(":sdk-workflow-api"))

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.jsr310)

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.protobuf.kotlin)

  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(vertxLibs.vertx.kotlin.coroutines)

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
