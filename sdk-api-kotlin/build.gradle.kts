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
  api(project(":sdk-serde-kotlinx"))

  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.kotlin)
}
