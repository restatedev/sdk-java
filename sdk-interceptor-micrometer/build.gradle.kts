plugins {
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
}

description = "Restate SDK Micrometer Observation interceptor"

dependencies {
  compileOnly(libs.jspecify)

  // Both API flavors are compile-only. Consumers bring in whichever they're using.
  compileOnly(project(":sdk-api"))
  compileOnly(project(":sdk-api-kotlin"))

  // Used by both Java and Kotlin classes.
  api(libs.micrometer.observation)
  api(libs.micrometer.context.propagation)

  // Kotlin-only runtime deps.
  compileOnly(libs.kotlinx.coroutines.core)
}
