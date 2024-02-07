plugins {
  java
  application
  `library-publishing-conventions`
}

description = "Restate SDK API Gen"

dependencies {
  implementation(project(":sdk-common"))
  implementation(project(":sdk-api"))
  implementation(project(":sdk-workflow-api"))
  implementation(project(":sdk-serde-jackson"))

  implementation("com.github.jknack:handlebars:4.3.1")
}
