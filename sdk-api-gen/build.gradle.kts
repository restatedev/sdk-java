plugins {
  `java-conventions`
  application
  `library-publishing-conventions`
}

description =
    "Restate SDK API Gen (DEPRECATED: the annotation-processor/codegen API is deprecated in favor of the reflection-based API; see https://github.com/restatedev/sdk-java/blob/main/MIGRATION.md)"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":sdk-api-gen-common"))
  implementation(project(":sdk-api"))
}
