plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  api(libs.kotlinx.serialization.json)
  api(project(":sdk-common"))
  api(project(":sdk-serde-kotlinx"))

  // For concrete class proxying in service-to-service calls
  runtimeOnly(project(":bytebuddy-proxy-support"))

  implementation(project(":common-kotlin"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(kotlin("reflect"))
  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.kotlin)
}
