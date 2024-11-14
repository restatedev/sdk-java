plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Jackson integration"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(platform(jacksonLibs.jackson.bom))
  api(jacksonLibs.jackson.databind)
  implementation(jacksonLibs.jackson.core)

  implementation("com.github.victools:jsonschema-generator:4.37.0")
  implementation("com.github.victools:jsonschema-module-jackson:4.37.0")

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)

  implementation(project(":sdk-common"))
}
