plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Common types used by different Restate Java modules"

dependencies {
  compileOnly(libs.jspecify)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
}
