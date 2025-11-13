plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlinx Serialization integration"

dependencies {
  api(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.schema.kenerator.core)
  implementation(libs.schema.kenerator.serialization)
  implementation(libs.schema.kenerator.jsonschema)

  implementation(project(":common"))

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
