plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Jackson integration"

dependencies {
  api(jacksonLibs.jackson.databind)
  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)

  implementation(project(":sdk-common"))
}
