plugins {
  `kotlin-conventions`
  `test-jar-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Core Kotlin extensions"

dependencies {
  api(project(":sdk-core"))
}
