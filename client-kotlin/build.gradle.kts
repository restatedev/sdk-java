plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate Client to interact with services from within other Kotlin applications"

dependencies {
  api(project(":client")) { exclude("dev.restate", "sdk-serde-jackson") }
  api(project(":sdk-serde-kotlinx"))

  implementation(libs.kotlinx.coroutines.core)
}
