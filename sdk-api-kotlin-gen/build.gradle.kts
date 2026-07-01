plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.ksp)
}

description =
    "Restate SDK API Kotlin Gen (DEPRECATED: the KSP code-generator/codegen API is deprecated in favor of the reflection-based API; see https://github.com/restatedev/sdk-java/blob/main/MIGRATION.md)"

dependencies {
  compileOnly(libs.jspecify)

  implementation(libs.ksp.api)
  implementation(project(":sdk-api-gen-common"))

  implementation(project(":sdk-api-kotlin"))
}
