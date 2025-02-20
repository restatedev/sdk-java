plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK APIs"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-common"))
  api(project(":sdk-serde-jackson"))

  implementation(libs.log4j.api)
}
