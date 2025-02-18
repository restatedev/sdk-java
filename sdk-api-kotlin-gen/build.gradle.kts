plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
}

description = "Restate SDK API Kotlin Gen"

dependencies {
  compileOnly(libs.jspecify)

  implementation(libs.ksp.api)
  implementation(project(":sdk-api-gen-common"))

  implementation(project(":sdk-api-kotlin"))
}
