plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlinx Serialization integration"

dependencies {
  api(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.serialization.core)

  implementation(project(":common"))
}
