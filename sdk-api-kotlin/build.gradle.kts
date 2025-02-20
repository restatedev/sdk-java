plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  api(libs.kotlinx.serialization.json)

  api(project(":sdk-common"))

  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.kotlin)
}
