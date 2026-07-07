import org.gradle.kotlin.dsl.withType
import org.jetbrains.dokka.gradle.tasks.DokkaGenerateTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  `java-conventions`
  `kotlin-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.jsonschema2pojo)
  alias(libs.plugins.ksp)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.shadow)
  alias(libs.plugins.jextract)

  // https://github.com/gradle/gradle/issues/20084#issuecomment-1060822638
  id(libs.plugins.spotless.get().pluginId) apply false
}

description = "Restate SDK Core"

// ===========================================================================
// Paths, native target & generated directories
// ===========================================================================

val rustSrcDir = file("src/main/rust")

// Host target for local builds; CI cross-compiles the full matrix and overlays
// the per-platform binaries into the same resource layout before packaging.
val hostRustTarget = "x86_64-unknown-linux-gnu"
val hostNativeClassifier = "linux-x86_64"
val nativeLibFileName = "librestate_sdk_core.so"

// Release overlay: when `-PnativeLibsDir=<dir>` is set (by the release pipeline), `<dir>` is a
// directory already laid out as
// `dev/restate/sdk/core/native/<classifier>/librestate_sdk_core.<ext>`
// holding the cross-compiled libraries for every supported platform (built by
// .github/workflows/native.yaml). When absent (PRs / local dev) only the host library is built and
// packaged.
val nativeLibsDir = providers.gradleProperty("nativeLibsDir")

// Every platform classifier the released uber jar must ship. Kept in sync with the target matrix in
// .github/workflows/native.yaml and the classifiers resolved by NativeLibraryLoader at runtime.
val releaseNativeClassifiers =
    listOf(
        "linux-x86_64",
        "linux-aarch64",
        "linux-x86_64-musl",
        "linux-aarch64-musl",
        "darwin-aarch64",
    )

// Generated outputs (all under build/): the cbindgen C header consumed by jextract, the native-lib
// resource tree, the jsonSchema2Pojo sources, and the protoc sources.
val generatedHeaderFile = layout.buildDirectory.file("generated/jextract-header/sharedcore.h")
val nativeResourceDir = layout.buildDirectory.dir("native-resource")
val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")
val generatedProtoDir = layout.buildDirectory.dir("generated/source/proto/main/java")

// ===========================================================================
// Dependency configurations
//
//   shade  -> bundled & relocated into the shadow jar (protobuf).
//   shadow -> exported via the POM but NOT bundled; kept on the compile + runtime classpath so we
//             compile and run against it (the `shadow` config is created by the shadow plugin).
// ===========================================================================

val shade by configurations.creating

val implementation by configurations.getting

implementation.extendsFrom(shade)

val api by configurations.getting

api.extendsFrom(shade)

// Put the `shadow` deps on the runtime classpath. The shadow plugin already adds them to
// compileClasspath automatically, but NOT to runtimeClasspath — so we wire only that one.
configurations["runtimeClasspath"].extendsFrom(configurations["shadow"])

// ===========================================================================
// Source sets
// ===========================================================================

sourceSets {
  val main by getting {
    java.srcDir(generatedJ2SPDir)
    java.srcDir(generatedProtoDir)
    resources.srcDir(nativeResourceDir)
    proto { srcDirs("src/main/service-protocol") }
  }

  val java23 by creating {
    java.setSrcDirs(listOf("src/main/java23"))
    compileClasspath += main.output + main.compileClasspath
    runtimeClasspath += main.output + main.runtimeClasspath
  }

  // Allow access to java23 code.
  val test by getting {
    compileClasspath += java23.output
    runtimeClasspath += java23.output
  }

  // JMH sources depend on main, java23 and test classpaths.
  //
  // These local outputs must PRECEDE the resolved dependency configurations: jmhImplementation
  // extends testImplementation, which pulls sibling projects (sdk-api, sdk-http-vertx, …) that each
  // depend on the *shaded* sdk-core jar (protobuf relocated to
  // dev.restate.shaded.com.google.protobuf).
  // If that jar shadows main.output, the generated Protocol classes resolve to the
  // relocated-protobuf
  // copy and ProtoUtils' unshaded `setId(com.google.protobuf.ByteString)` calls fail at runtime
  // with
  // NoSuchMethodError. Prepending main/java23/test output makes the unshaded classes win (this
  // mirrors
  // the default `test` source set, which already lists main.output before the dependencies).
  val jmh by creating {
    java.setSrcDirs(listOf("src/jmh/java"))
    compileClasspath = main.output + java23.output + test.output + compileClasspath
    runtimeClasspath = main.output + java23.output + test.output + runtimeClasspath
  }
}

// Give the benchmarks everything the tests have (ProtoUtils' deps: protobuf, sdk-common, …).
configurations["jmhImplementation"].extendsFrom(configurations["testImplementation"])

configurations["jmhRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

// Referenced downstream (packaging + jextract); the in-block vals above are block-scoped.
val java23SourceSet = sourceSets["java23"]

// ===========================================================================
// Dependencies
// ===========================================================================

dependencies {
  compileOnly(libs.jspecify)

  // Runtime deps exported via the POM but NOT bundled into the shadow jar.
  shadow(project(":sdk-common"))
  shadow(libs.log4j.api)
  shadow(libs.opentelemetry.api)
  // Jackson for the endpoint manifest (jsonSchema2Pojo-generated POJOs)
  shadow(libs.jackson.annotations)
  shadow(libs.jackson.databind)

  // Shaded + relocated into the jar: the pure-Java state machine's wire codec uses protobuf
  shade(libs.protobuf.java)

  // We don't want a hard-dependency on it
  compileOnly(libs.log4j.core)

  // java23 (FFM) overlay
  "java23CompileOnly"(libs.jspecify)

  testCompileOnly(libs.jspecify)
  testAnnotationProcessor(project(":sdk-api-gen"))
  kspTest(project(":sdk-api-kotlin-gen"))
  testImplementation(libs.log4j.api)
  testImplementation(project(":sdk-common"))
  testImplementation(project(":client"))
  testImplementation(project(":client-kotlin"))
  testImplementation(project(":sdk-api"))
  testImplementation(project(":sdk-api-kotlin"))
  testImplementation(project(":sdk-http-vertx"))
  testImplementation(project(":sdk-lambda"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)
  testImplementation(libs.protobuf.java)
  testImplementation(libs.opentelemetry.api)
  testImplementation(libs.mutiny)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
  testImplementation(libs.log4j.core)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.core)
  testImplementation(libs.vertx.junit5)
  testImplementation(libs.vertx.kotlin.coroutines)
  testRuntimeOnly(libs.junit.platform.launcher)

  // JMH benchmarks dependencies
  "jmhImplementation"("org.openjdk.jmh:jmh-core:1.37")
  "jmhAnnotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

// ===========================================================================
// Native build: Rust cargo build (cdylib + cbindgen header) and jextract FFM bindings
// ===========================================================================

val cargoBuild by
    tasks.registering(Exec::class) {
      group = "build"
      description = "Compile the Rust shared-core wrapper to a native cdylib and emit the C header"
      workingDir = rustSrcDir
      environment("SHARED_CORE_HEADER_OUT", generatedHeaderFile.get().asFile.absolutePath)
      commandLine("cargo", "build", "--release", "--target", hostRustTarget)
      standardOutput = System.out
      errorOutput = System.err
      inputs.dir("$rustSrcDir/src")
      inputs.file("$rustSrcDir/Cargo.toml")
      inputs.file("$rustSrcDir/build.rs")
      outputs.file(generatedHeaderFile)
      outputs.file("$rustSrcDir/target/$hostRustTarget/release/$nativeLibFileName")
    }

val copyNativeLib by
    tasks.registering(Copy::class) {
      group = "build"
      into(nativeResourceDir)
      // nativeResourceDir is owned exclusively by this task; wipe it first so its contents always
      // match the current source and a stale library (e.g. a host build left over before switching
      // to the release overlay) can never leak into the packaged jar.
      doFirst { delete(nativeResourceDir) }
      if (nativeLibsDir.isPresent) {
        // Release path: overlay the pre-built, cross-compiled libraries produced by
        // .github/workflows/native.yaml. The directory already contains the
        // dev/restate/sdk/core/native/<classifier>/ layout, so copy it verbatim. `cargoBuild` still
        // runs (compileJava23Java / jextract need its C header) but its host .so is not packaged —
        // the linux-x86_64 library comes from the overlay like every other platform.
        from(nativeLibsDir)
      } else {
        // Local / PR path: build and package only the host library.
        dependsOn(cargoBuild)
        from("$rustSrcDir/target/$hostRustTarget/release/$nativeLibFileName") {
          into("dev/restate/sdk/core/native/$hostNativeClassifier")
        }
      }
    }

val cargoFmt by
    tasks.registering(Exec::class) {
      group = "formatting"
      description = "Format the Rust wrapper crate with cargo fmt"
      workingDir = rustSrcDir
      commandLine("cargo", "fmt")
    }

tasks.matching { it.name == "spotlessApply" }.configureEach { dependsOn(cargoFmt) }

// The native lib is a main resource, so it must be copied before resources are processed.
tasks.named("processResources") { dependsOn(copyNativeLib) }

// jextract bindings are generated into the java23 (FFM) source set only — they reference
// java.lang.foreign, which is not available at the Java 17 base level.
jextract.libraries {
  val sharedCore by registering {
    header.set(generatedHeaderFile)
    headerClassName = "SharedCoreNative"
    targetPackage = "dev.restate.sdk.core.statemachine.ffm.generated"
  }
  sourceSets.named("java23") { jextract.libraries.addLater(sharedCore) }
}

// The C header is produced by the Rust build; jextract must run after it.
tasks
    .matching { it.name == "generateSharedCoreBindings" || it.name == "dumpSharedCoreIncludes" }
    .configureEach { dependsOn(cargoBuild) }

// ===========================================================================
// Code generation: protobuf, jsonSchema2Pojo, ksp
//
// Downstream consumers of the generated sources (compile/jar/dokka) are wired in the Compilation
// section below.
// ===========================================================================

// Protobuf: the Restate wire protocol for the pure-Java state machine.
protobuf { protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" } }

// jsonSchema2Pojo: the endpoint manifest POJOs.
jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/service-protocol/endpoint_manifest_schema.json"))
  targetPackage = "dev.restate.sdk.core.generated.manifest"
  targetDirectory = generatedJ2SPDir.get().asFile
  useLongIntegers = true
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

// ===========================================================================
// Compilation & cross-cutting task wiring
// ===========================================================================

// main + test inherit --release 17 from `java-conventions`; the FFM overlay must compile at 23
// (java.lang.foreign is stable there), overriding that default.
tasks.named<JavaCompile>("compileJava23Java") {
  options.release = 23
  // header must exist before jextract runs against it
  dependsOn(cargoBuild)
}

// Codegen tasks
val codegen = arrayOf(tasks.named("generateProto"), tasks.named("generateJsonSchema2Pojo"))

tasks {
  withType<JavaCompile> {
    dependsOn(*codegen)

    // Skip reflection based classes in annotation processor (this is only testing)
    val disabledClassesCodegen =
        listOf(
            "dev.restate.sdk.core.javaapi.reflections.CheckedException",
            "dev.restate.sdk.core.javaapi.reflections.CustomSerde",
            "dev.restate.sdk.core.javaapi.reflections.Empty",
            "dev.restate.sdk.core.javaapi.reflections.GreeterInterface",
            "dev.restate.sdk.core.javaapi.reflections.MyWorkflow",
            "dev.restate.sdk.core.javaapi.reflections.ObjectGreeter",
            "dev.restate.sdk.core.javaapi.reflections.ObjectGreeterImplementedFromInterface",
            "dev.restate.sdk.core.javaapi.reflections.PrimitiveTypes",
            "dev.restate.sdk.core.javaapi.reflections.RawInputOutput",
            "dev.restate.sdk.core.javaapi.reflections.RawService",
            "dev.restate.sdk.core.javaapi.reflections.ServiceGreeter",
        )

    options.compilerArgs.addAll(
        listOf(
            "-parameters",
            "-Adev.restate.codegen.disabledClasses=${disabledClassesCodegen.joinToString(",")}",
        )
    )
  }
  withType<KotlinCompile>().configureEach { dependsOn(*codegen) }
  withType<org.gradle.jvm.tasks.Jar>().configureEach {
    // Multi-release jar so the FFM overlay under META-INF/versions/23 is honored on JDK >= 23.
    manifest { attributes("Multi-Release" to "true") }
    dependsOn(*codegen)
  }
  withType<DokkaGenerateTask>().configureEach { dependsOn(*codegen) }
}

// Skip reflection based classes in ksp (this is only testing)
ksp {
  val disabledClassesCodegen =
      listOf(
          "dev.restate.sdk.core.kotlinapi.reflections.CheckedException",
          "dev.restate.sdk.core.kotlinapi.reflections.CustomSerdeService",
          "dev.restate.sdk.core.kotlinapi.reflections.Empty",
          "dev.restate.sdk.core.kotlinapi.reflections.GreeterInterface",
          "dev.restate.sdk.core.kotlinapi.reflections.NestedDataClass",
          "dev.restate.sdk.core.kotlinapi.reflections.CornerCases",
          "dev.restate.sdk.core.kotlinapi.reflections.GreeterWithExplicitName",
          "dev.restate.sdk.core.kotlinapi.reflections.MyWorkflow",
          "dev.restate.sdk.core.kotlinapi.reflections.ObjectGreeter",
          "dev.restate.sdk.core.kotlinapi.reflections.ObjectGreeterImplementedFromInterface",
          "dev.restate.sdk.core.kotlinapi.reflections.PrimitiveTypes",
          "dev.restate.sdk.core.kotlinapi.reflections.RawInputOutput",
          "dev.restate.sdk.core.kotlinapi.reflections.ServiceGreeter",
      )
  arg("dev.restate.codegen.disabledClasses", disabledClassesCodegen.joinToString(","))
}

// ===========================================================================
// Packaging: the published artifact is a shaded, multi-release jar (relocated protobuf, the native
// lib in resources, and the FFM overlay). The plain jar is disabled.
// ===========================================================================

tasks {
  named("jar") {
    enabled = false
    dependsOn("shadowJar")
  }
  shadowJar {
    dependsOn(copyNativeLib)
    manifest { attributes("Automatic-Module-Name" to "dev.restate.sdk.core") }
    // Bundle only the `shade` config (protobuf); `shadow` deps stay external (POM only).
    configurations = listOf(shade)
    enableRelocation = true
    archiveClassifier = null
    relocate("com.google.protobuf", "dev.restate.shaded.com.google.protobuf")
    // Carry the multi-release FFM overlay.
    into("META-INF/versions/23") { from(java23SourceSet.output) }
    dependencies {
      project.configurations["shadow"].allDependencies.forEach { exclude(dependency(it)) }
      exclude("**/google/protobuf/*.proto")
    }
  }
}

// ===========================================================================
// Release verification: the published uber jar must bundle a native library for every supported
// platform. The release pipeline runs `:sdk-core:verifyNativeLibs -PnativeLibsDir=<dir>` after the
// overlay so a missing/misnamed cross-build fails the release instead of shipping a jar that only
// works on some platforms.
// ===========================================================================
val verifyNativeLibs by
    tasks.registering {
      group = "verification"
      description = "Assert the shaded jar bundles a native library for every required classifier"
      dependsOn("shadowJar")
      doLast {
        val jar = tasks.named<org.gradle.jvm.tasks.Jar>("shadowJar").get().archiveFile.get().asFile
        // zipTree extracts the jar; a bundled lib lives at
        // dev/restate/sdk/core/native/<classifier>/librestate_sdk_core.<ext>, so the file's parent
        // dir name is the classifier and its grandparent is "native".
        val present =
            zipTree(jar)
                .files
                .filter {
                  it.name.startsWith("librestate_sdk_core") &&
                      it.parentFile?.parentFile?.name == "native"
                }
                .map { it.parentFile.name }
                .toSortedSet()
        val missing = releaseNativeClassifiers.filterNot(present::contains)
        if (missing.isNotEmpty()) {
          throw GradleException(
              "sdk-core jar $jar is missing native libraries for classifiers $missing " +
                  "(present: $present). Did the native.yaml cross-build + overlay run?"
          )
        }
        logger.lifecycle("Verified sdk-core bundles native libraries for classifiers: $present")
      }
    }

// ===========================================================================
// JMH run task. `./gradlew :sdk-core:jmh` runs all benchmarks; pass JMH CLI args via
// `-PjmhArgs="SysCall -f 1 -wi 3 -i 5 -prof gc"`. Runs on the JDK 25 toolchain (FFM enabled).
// ===========================================================================
val jmh by
    tasks.registering(JavaExec::class) {
      group = "benchmark"
      description = "Run JMH benchmarks (FfmStateMachine / shared-core boundary)"
      dependsOn("jmhClasses", copyNativeLib)
      javaLauncher.set(javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) })
      mainClass.set("org.openjdk.jmh.Main")
      classpath = sourceSets["jmh"].runtimeClasspath
      // JDK 25 enables FFM by default; silence the native-access warning.
      jvmArgs("--enable-native-access=ALL-UNNAMED")
      // Force the benchmark log4j2 config (logging OFF) over the tests' TRACE config, which is on
      // the
      // classpath via test.output — logging would otherwise dominate the per-op cost.
      systemProperty(
          "log4j2.configurationFile",
          file("src/jmh/resources/log4j2.properties").absolutePath,
      )
      (project.findProperty("jmhArgs") as String?)?.let {
        if (it.isNotBlank()) args(it.trim().split(" "))
      }
    }
