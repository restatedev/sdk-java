plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(kotlin("reflect"))
  api(libs.kotlinx.serialization.json)

  api(project(":sdk-common"))
  api(project(":sdk-serde-kotlinx"))

  // For concrete class proxying in service-to-service calls
  runtimeOnly(project(":bytebuddy-proxy-support"))

  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.kotlin)
}
