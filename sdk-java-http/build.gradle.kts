plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Java HTTP starter"

dependencies {
  api(project(":sdk-api"))
  api(project(":sdk-http-vertx"))
  api(project(":client"))
  implementation(libs.log4j.core)
}
