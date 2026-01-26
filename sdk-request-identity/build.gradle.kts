plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK request identity implementation"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":sdk-common"))

  // Dependencies for signing request tokens
  implementation(libs.jwt)
  implementation(libs.tink)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testRuntimeOnly(libs.junit.platform.launcher)
}
