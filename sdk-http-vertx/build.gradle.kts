plugins {
  `java-library`
  kotlin("jvm")
  `library-publishing-conventions`
}

description = "Restate SDK HTTP implementation based on Vert.x"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  // Vert.x
  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)

  // Observability
  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.log4j.api)
  implementation("io.reactiverse:reactiverse-contextual-logging:1.2.1")

  // Testing
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-serde-jackson"))
  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-core", "testArchive"))
  testImplementation(project(":sdk-api", "testArchive"))
  testImplementation(project(":sdk-api-gen", "testArchive"))
  testImplementation(project(":sdk-api-kotlin", "testArchive"))
  testImplementation(project(":sdk-api-kotlin-gen", "testArchive"))
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(vertxLibs.vertx.junit5)
  testImplementation("io.smallrye.reactive:mutiny:2.6.0")

  testImplementation(coreLibs.protobuf.java)
  testImplementation(coreLibs.protobuf.kotlin)
  testImplementation(coreLibs.log4j.core)

  testImplementation(kotlinLibs.kotlinx.coroutines)
  testImplementation(vertxLibs.vertx.kotlin.coroutines)
}
