plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Jackson integration"

dependencies {
  compileOnly(libs.jspecify)

  api(libs.jackson.databind)
  implementation(libs.jackson.core)

  implementation(libs.victools.jsonschema.generator)
  implementation(libs.victools.jsonschema.module.jackson)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)

  implementation(project(":sdk-common"))
}
