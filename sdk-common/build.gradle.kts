plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Common interfaces of the Restate SDK"

dependencies {
  api(coreLibs.protobuf.java)
  api(coreLibs.grpc.api)

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)
  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}
