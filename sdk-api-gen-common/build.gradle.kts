plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK API Gen Common"

dependencies {
  compileOnly(libs.jspecify)

  api(libs.handlebars) {
    // exclude nashorn-core, since it is not license compliant and is only
    // needed for javascript in templates which we don't use.
    exclude(group = "org.openjdk.nashorn", module = "nashorn-core")
  }
  api(project(":sdk-common"))

  // We need it to silence the slf4j warning (coming from handlebars)
  runtimeOnly(libs.slf4j.nop)
}
