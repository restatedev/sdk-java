plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Java Lambda starter"

dependencies {
  api(project(":sdk-api"))
  api(project(":sdk-lambda"))
  implementation(libs.log4j.core)
}
