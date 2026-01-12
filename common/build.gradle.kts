plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Common types used by different Restate Java modules"

dependencies {
  compileOnly(libs.jspecify)

  implementation(libs.log4j.api)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
}

tasks.withType<Javadoc> { isFailOnError = false }
