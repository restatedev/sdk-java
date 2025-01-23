plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Kotlin APIs"

dependencies {
  api(project(":sdk-common"))
  implementation(libs.kotlinx.coroutines.core)
}
