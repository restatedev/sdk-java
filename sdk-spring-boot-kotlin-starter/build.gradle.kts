plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.spring.dependency.management)
}

description = "Restate SDK Spring Boot Kotlin starter"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-api-kotlin"))
  api(project(":client-kotlin"))
  api(project(":sdk-spring-boot"))

  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(project(":sdk-testing"))
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.spring.boot.starter.test)

  // We need these for the deployment manifest
  testImplementation(project(":sdk-core"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)
}
