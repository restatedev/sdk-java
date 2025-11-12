plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
}

description = "Restate SDK Spring Boot Kotlin starter"

dependencies {
  compileOnly(libs.jspecify)

  // SDK dependencies
  api(project(":sdk-api-kotlin"))
  api(project(":client-kotlin"))
  api(project(":sdk-serde-kotlinx"))
  api(project(":sdk-spring-boot"))

  // Spring boot starter for kotlin dependencies brought in here for convenience
  api(libs.spring.boot.starter)
  implementation("org.jetbrains.kotlin:kotlin-reflect")

  // Testing
  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(project(":sdk-testing"))
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(project(":sdk-core"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)
}
