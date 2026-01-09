plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate Client to interact with services from within other Java applications"

dependencies {
  compileOnly(libs.jspecify)
  compileOnly(libs.jetbrains.annotations)

  api(project(":common"))
  api(project(":sdk-serde-jackson"))

  implementation(libs.jackson.core)
  implementation(libs.log4j.api)
}
