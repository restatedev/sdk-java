plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Common interfaces of the Restate SDK"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(coreLibs.protobuf.java)

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}
