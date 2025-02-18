plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK HTTP implementation based on Vert.x"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-common"))
  implementation(project(":sdk-core"))

  // Vert.x
  implementation(libs.vertx.core)

  // Observability
  implementation(libs.opentelemetry.api)
  implementation(libs.log4j.api)
  implementation(libs.reactiverse.contextual.logging)
}
