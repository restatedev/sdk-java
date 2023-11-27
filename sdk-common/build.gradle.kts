plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Common interfaces of the Restate SDK"

dependencies {
  api(coreLibs.protobuf.java)
  api(coreLibs.grpc.api)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}
