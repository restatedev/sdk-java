import com.google.protobuf.gradle.id
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Without these suppressions version catalog usage here and in other build
// files is marked red by IntelliJ:
// https://youtrack.jetbrains.com/issue/KTIJ-19369.
@Suppress(
    "DSL_SCOPE_VIOLATION",
    "MISSING_DEPENDENCY_CLASS",
    "UNRESOLVED_REFERENCE_WRONG_RECEIVER",
    "FUNCTION_CALL_EXPECTED")
plugins {
  java
  kotlin("jvm") version "1.6.20"
  idea
  `maven-publish`
  application
}

dependencies {
  implementation(project(":sdk-blocking"))
  implementation(project(":sdk-vertx"))
  implementation(project(":sdk-kotlin"))
  implementation(project(":sdk-serde-jackson"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.protobuf.kotlin)
  implementation(coreLibs.grpc.stub)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.grpc.kotlin.stub) { exclude("javax.annotation", "javax.annotation-api") }

  // Replace javax.annotations-api with tomcat annotations
  compileOnly(coreLibs.javax.annotation.api)

  implementation(platform(vertxLibs.vertx.bom))
  implementation(vertxLibs.vertx.core)
  implementation(vertxLibs.vertx.kotlin.coroutines)
  implementation(vertxLibs.vertx.grpc.context.storage)

  implementation(kotlinLibs.kotlinx.coroutines)

  implementation(coreLibs.log4j.core)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
  kotlin {
    ktfmt()
    targetExclude("build/generated/**/*.kt")
  }
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
    id("grpckt") {
      artifact = "io.grpc:protoc-gen-grpc-kotlin:${coreLibs.versions.grpckt.get()}:jdk8@jar"
    }
  }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        id("grpc")
        id("grpckt")
      }
      it.builtins { id("kotlin") }

      // Generate descriptor set including the imports in order to make it easier to
      // invoke the services via grpcurl (using -protoset).
      it.generateDescriptorSet = true
      it.descriptorSetOptions.includeImports = true
    }
  }
}

application {
  val mainClassValue: String =
      project.findProperty("mainClass")?.toString() ?: "dev.restate.sdk.examples.BlockingCounter"
  mainClass.set(mainClassValue)
}

tasks.withType<KotlinCompile> { kotlinOptions.jvmTarget = "11" }