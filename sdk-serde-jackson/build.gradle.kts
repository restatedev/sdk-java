plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Jackson integration"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(platform(jacksonLibs.jackson.bom))
  api(jacksonLibs.jackson.databind)
  implementation(jacksonLibs.jackson.core)

  implementation(project(":sdk-common"))

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}
