plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Protobuf integration"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(coreLibs.protobuf.java)

  implementation(project(":sdk-common"))
}
