plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK API Gen Common"

dependencies {
  compileOnly(coreLibs.jspecify)

  api("com.github.jknack:handlebars:4.3.1")
  api(project(":sdk-common"))

  // We need it to silence the slf4j warning (coming from handlebars)
  runtimeOnly("org.slf4j:slf4j-nop:1.7.32")
}
