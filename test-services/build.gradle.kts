import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentArchitecture

plugins {
  `java-conventions`
  `kotlin-conventions`
  alias(libs.plugins.ksp)
  application
  alias(libs.plugins.jib)
}

dependencies {
  ksp(project(":sdk-api-kotlin-gen"))

  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-serde-jackson"))
  implementation(project(":sdk-request-identity"))

  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.log4j.core)

  implementation(libs.kotlinx.coroutines.core)
}

// Configuration of jib container images parameters

fun testHostArchitecture(): String {
  val currentArchitecture = getCurrentArchitecture()

  return if (currentArchitecture.isAmd64) {
    "amd64"
  } else {
    when (currentArchitecture.name) {
      "arm-v8",
      "aarch64",
      "arm64",
      "aarch_64" -> "arm64"
      else ->
          throw IllegalArgumentException("Not supported host architecture: $currentArchitecture")
    }
  }
}

fun testBaseImage(): String {
  return when (testHostArchitecture()) {
    "arm64" ->
        "eclipse-temurin:17-jre@sha256:61c5fee7a5c40a1ca93231a11b8caf47775f33e3438c56bf3a1ea58b7df1ee1b"
    "amd64" ->
        "eclipse-temurin:17-jre@sha256:ff7a89fe868ba504b09f93e3080ad30a75bd3d4e4e7b3e037e91705f8c6994b3"
    else ->
        throw IllegalArgumentException("No image for host architecture: ${testHostArchitecture()}")
  }
}

jib {
  to.image = "restatedev/java-test-services"
  from.image = testBaseImage()

  from {
    platforms {
      platform {
        architecture = testHostArchitecture()
        os = "linux"
      }
    }
  }
}

tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.sdk.testservices.MainKt" } }

application { mainClass.set("dev.restate.sdk.testservices.MainKt") }
