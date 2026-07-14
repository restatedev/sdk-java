plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate Client to interact with services from within other Kotlin applications"

configurations.all {
  // Gonna conflict with sdk-serde-kotlinx
  exclude(group = "dev.restate", module = "sdk-serde-jackson")
}

dependencies {
  api(project(":client"))
  api(project(":sdk-serde-kotlinx"))

  implementation(project(":common-kotlin"))
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.kotlinx.coroutines.test)
  testRuntimeOnly(libs.junit.platform.launcher)
}
