plugins {
  `java-conventions`
  application
  `library-publishing-conventions`
}

description = "Restate SDK API Gen"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":sdk-api-gen-common"))
  implementation(project(":sdk-api"))
}
