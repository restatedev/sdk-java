plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK API Gen Common"

dependencies {
  compileOnly(coreLibs.jspecify)

  api("com.github.jknack:handlebars:4.3.1")
  api(project(":sdk-common"))
}
