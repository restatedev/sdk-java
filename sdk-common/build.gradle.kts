plugins {
  `java-library`
  `library-publishing-conventions`
}

description = "Common interfaces of the Restate SDK"

dependencies {
  compileOnly(coreLibs.jspecify)

  api(coreLibs.protobuf.java)

  implementation(platform(jacksonLibs.jackson.bom))
  implementation(jacksonLibs.jackson.core)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
}

val generatedVersionDir = layout.buildDirectory.dir("version")

generatedVersionDir.get().asFile.mkdirs()

sourceSets { main { java { srcDir(generatedVersionDir) } } }

// Configure generation of version class

// From https://discuss.kotlinlang.org/t/use-git-hash-as-version-number-in-build-gradle-kts/19818/4
fun String.runCommand(
    workingDir: File = File("."),
    timeoutAmount: Long = 5,
    timeoutUnit: TimeUnit = TimeUnit.SECONDS
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
        val gitHash = "git rev-parse --short=8 HEAD".runCommand(workingDir = rootDir)
        val containingDir = generatedVersionDir.get().dir("dev/restate/sdk/version").asFile
        assert(containingDir.exists() || containingDir.mkdirs())

        file("$containingDir/Version.java")
            .writeText(
                """
      package dev.restate.sdk.version;
      
      public final class Version {
          private Version() {}
          
          public static final String VERSION = "$version";
          public static final String GIT_HASH = "$gitHash";
          public static final String X_RESTATE_SERVER = "restate-sdk-java/" + VERSION + "_" + GIT_HASH;
      }
      """
                    .trimIndent())

        check(file("${projectDir}/build/version/dev/restate/sdk/version/Version.java").exists()) {
          "${projectDir}/build/version/dev/restate/sdk/version/Version.java doesn't exist?!"
        }
      }
    }

tasks {
  withType<JavaCompile>().configureEach { dependsOn(generateVersionClass) }
  withType<Jar>().configureEach { dependsOn(generateVersionClass) }
}
