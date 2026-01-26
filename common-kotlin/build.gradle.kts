plugins {
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Common types used by different Restate Kotlin modules"

dependencies {
  api(project(":common"))
  api(project(":sdk-serde-kotlinx"))
  implementation(kotlin("reflect"))
}
