plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Fake APIs for mocking"

dependencies {
  compileOnly(libs.jspecify)
  compileOnly(libs.jetbrains.annotations)

  api(project(":sdk-api"))
  implementation(project(":common"))
  implementation(project(":sdk-core"))
  implementation(project(":sdk-serde-jackson"))
  implementation(libs.log4j.api)
  implementation(libs.junit.api)
}
