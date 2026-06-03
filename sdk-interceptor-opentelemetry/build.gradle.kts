plugins {
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK OpenTelemetry tracing interceptor (Java + Kotlin)"

dependencies {
  compileOnly(libs.jspecify)
  compileOnly(libs.jetbrains.annotations)

  // Both API flavors are compile-only. Consumers bring in whichever they're using.
  compileOnly(project(":sdk-api"))
  compileOnly(project(":sdk-api-kotlin"))

  // Used by both Java and Kotlin classes.
  api(libs.opentelemetry.api)

  // Kotlin-only runtime deps. Kotlin consumers get them transitively via sdk-api-kotlin.
  compileOnly(libs.opentelemetry.kotlin)
  compileOnly(libs.kotlinx.coroutines.core)
}
