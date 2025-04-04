plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Jackson integration"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":common"))

  api(libs.jackson.databind)
  implementation(libs.jackson.core)

  implementation(libs.victools.jsonschema.generator)
  implementation(libs.victools.jsonschema.module.jackson)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
}

tasks.withType<Javadoc> { isFailOnError = false }
