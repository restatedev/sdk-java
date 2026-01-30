plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
  alias(libs.plugins.spring.dependency.management)
}

description = "Restate SDK Spring Boot Kotlin starter"

configurations.all {
  // Gonna conflict with sdk-serde-kotlinx
  exclude(group = "dev.restate", module = "sdk-serde-jackson")
}

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
  testRuntimeOnly(libs.junit.platform.launcher)
}

ksp {
  val disabledClassesCodegen =
      listOf(
          "dev.restate.sdk.springboot.kotlin.GreeterNewApi",
      )
  arg("dev.restate.codegen.disabledClasses", disabledClassesCodegen.joinToString(","))
}
