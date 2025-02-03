plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Java Lambda starter"

dependencies {
  api(project(":sdk-api"))
  api(project(":sdk-lambda"))
  api(project(":client"))
  implementation(libs.log4j.core)
}
