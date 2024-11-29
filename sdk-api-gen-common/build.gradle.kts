plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK API Gen Common"

dependencies {
  compileOnly(libs.jspecify)

  api(libs.handlebars)
  api(project(":sdk-common"))

  // We need it to silence the slf4j warning (coming from handlebars)
  runtimeOnly(libs.slf4j.nop)
}
