plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate Client to interact with services from within other Kotlin applications"

dependencies {
  api(project(":client"))
  implementation(libs.kotlinx.coroutines.core)
}
