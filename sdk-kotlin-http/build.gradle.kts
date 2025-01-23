plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin HTTP starter"

dependencies {
  api(project(":sdk-api-kotlin"))
  api(project(":sdk-http-vertx"))
  implementation(libs.log4j.core)
}
