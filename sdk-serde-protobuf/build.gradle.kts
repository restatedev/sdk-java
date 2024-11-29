plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Protobuf integration"

dependencies {
  compileOnly(libs.jspecify)

  api(libs.protobuf.java)

  implementation(project(":sdk-common"))
}
