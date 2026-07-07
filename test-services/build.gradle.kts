import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentArchitecture

plugins {
  `java-conventions`
  `kotlin-conventions`
  application
  alias(libs.plugins.jib)
}

dependencies {
  implementation(project(":sdk-kotlin-http"))
  implementation(project(":sdk-request-identity"))

  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.log4j.core)
  implementation(libs.kotlinx.coroutines.core)

  // You might be wondering why I'm repeating these dependencies here. Well, don't, it's gradle.
  implementation(project(":sdk-common"))
  implementation(libs.log4j.api)
  implementation(libs.opentelemetry.api)
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.databind)
}

// Jib validates the base image's Java version against targetCompatibility. Our bytecode is built at
// --release 17 (via the conventions), but targetCompatibility otherwise reports the toolchain's 25,
// making Jib reject the Java 17 base image. Declare 17 so Jib sees the real target.
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
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

// JRE version of the test-services image. Parameterized so the conformance suite can run against
// both the minimum supported Java (17 -> pure-Java state machine) and a JDK that activates the
// Panama/FFM state machine (>= 23). Override with -PtestServicesJre=25.
val testServicesJre: String = (project.findProperty("testServicesJre") as String?) ?: "17"

jib {
  to.image = "restatedev/test-services-java"
  from.image = "eclipse-temurin:$testServicesJre-jre"

  from {
    platforms {
      platform {
        architecture = testHostArchitecture()
        os = "linux"
      }
    }
  }

  if (testServicesJre.toInt() >= 23) {
    container { jvmFlags = listOf("--enable-native-access=dev.restate.sdk.core") }
  }
}

tasks.jar { manifest { attributes["Main-Class"] = "dev.restate.sdk.testservices.MainKt" } }

application { mainClass.set("dev.restate.sdk.testservices.MainKt") }
