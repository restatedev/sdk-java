plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description =
    "Restate SDK API Gen Common (DEPRECATED: shared utilities for the deprecated codegen API; see https://github.com/restatedev/sdk-java/blob/main/MIGRATION.md)"

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
