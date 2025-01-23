plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin Lambda starter"

dependencies {
  api(project(":sdk-api-kotlin"))
  api(project(":sdk-lambda"))
  implementation(libs.log4j.core)
}
