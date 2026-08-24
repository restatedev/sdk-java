import com.google.protobuf.gradle.id
import java.io.File
import java.util.concurrent.TimeUnit

plugins {
  `java-library`
  `java-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.protobuf)
}

description = "Client for the Restate ingress integration (ingestion) API"

dependencies {
  // Generated protobuf messages + gRPC stubs are part of the public API surface.
  api(libs.protobuf.java)
  api(libs.grpc.protobuf)
  api(libs.grpc.stub)

  // Netty transport shipped as the default runtime channel implementation.
  implementation(libs.grpc.netty.shaded)

  // grpc-java generated stubs reference javax.annotation.Generated.
  compileOnly(libs.tomcat.annotations)

  // @ApiStatus.Experimental markers on the public API.
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.jspecify)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.protobuf.java)
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(project(":sdk-testing"))
  // In-process transport to drive the client against a fake IngestionSvc in unit tests.
  testImplementation(libs.grpc.inprocess)
  testRuntimeOnly(libs.log4j.core)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// Code generation: protobuf messages + gRPC Java stubs for the ingestion service.
protobuf {
  protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
  plugins { id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" } }
  generateProtoTasks { all().forEach { it.plugins { id("grpc") } } }
}

// Generate a `Version` class at build time. VERSION comes from the Gradle `version`, GIT_HASH from
// git; INTEGRATION is the default `name/version` identity stamped into the ingestion Start frame
// when the caller doesn't provide its own via IntegrationClient.Builder.integration(...).
val generatedVersionDir = layout.buildDirectory.dir("version")

generatedVersionDir.get().asFile.mkdirs()

// The protobuf plugin already registers the generated proto/grpc sources; only the version dir
// needs wiring here.
sourceSets { main { java { srcDir(generatedVersionDir) } } }

// From https://discuss.kotlinlang.org/t/use-git-hash-as-version-number-in-build-gradle-kts/19818/4
fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 5,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS,
): String =
    ProcessBuilder(split("\\s(?=(?:[^'\"`]*(['\"`])[^'\"`]*\\1)*[^'\"`]*$)".toRegex()))
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
        .apply { waitFor(timeoutAmount, timeoutUnit) }
        .run {
          val error = errorStream.bufferedReader().readText().trim()
          if (error.isNotEmpty()) {
            throw IllegalStateException(error)
          }
          inputStream.bufferedReader().readText().trim()
        }

val generateVersionClass =
    tasks.register("generateVersionClass") {
      dependsOn(project.tasks.processResources)
      outputs.dir(generatedVersionDir)

      doFirst {
        // Tolerate a checkout with no commits yet (git rev-parse fails before the first commit).
        val gitHash =
            try {
              "git rev-parse --short=8 HEAD".runCommand(workingDir = rootDir).ifBlank { "unknown" }
            } catch (e: Exception) {
              "unknown"
            }
        val containingDir = generatedVersionDir.get().dir("dev/restate/integration").asFile
        assert(containingDir.exists() || containingDir.mkdirs())

        file("$containingDir/Version.java")
            .writeText(
                """
      package dev.restate.integration;

      /** Generated at build time by the `generateVersionClass` Gradle task. Do not edit. */
      public final class Version {
          private Version() {}

          public static final String VERSION = "$version";
          public static final String GIT_HASH = "$gitHash";
          // Default integration identifier for the ingestion Start frame: `name/version`.
          public static final String INTEGRATION = "restate-integration-client/" + VERSION + "_" + GIT_HASH;
      }
      """
                    .trimIndent()
            )
      }
    }

tasks {
  withType<JavaCompile>().configureEach { dependsOn(generateVersionClass) }
  withType<org.gradle.jvm.tasks.Jar>().configureEach { dependsOn(generateVersionClass) }
}
