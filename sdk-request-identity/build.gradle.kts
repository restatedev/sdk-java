plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK request identity implementation"

dependencies {
  compileOnly(coreLibs.jspecify)

  implementation(project(":sdk-common"))

  // Dependencies for signing request tokens
  implementation(coreLibs.jwt)
  implementation(coreLibs.tink)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}
