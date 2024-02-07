import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.protobuf

plugins {
  java
  kotlin("jvm")
  application
  id("com.github.johnrengelman.shadow").version("7.1.2")
}

dependencies {
  annotationProcessor(project(":sdk-api-gen"))

  implementation(project(":sdk-api"))
  implementation(project(":sdk-lambda"))
  implementation(project(":sdk-http-vertx"))
  implementation(project(":sdk-api-kotlin"))
  implementation(project(":sdk-serde-jackson"))
  implementation(project(":sdk-workflow-api"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.protobuf.kotlin)
  implementation(coreLibs.grpc.stub)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.grpc.netty)
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

val pluginJar =
    file(
        "${project.rootProject.rootDir}/protoc-gen-restate/build/libs/protoc-gen-restate-${project.version}-all.jar")

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
    id("restate") {
      // NOTE: This is not needed in a regular project configuration, you should rather use:
      // artifact = "dev.restate.sdk:protoc-gen-restate-java-blocking:1.0-SNAPSHOT:all@jar"
      path = pluginJar.path
    }
  }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.dependsOn(":protoc-gen-restate:shadowJar")
      it.plugins {
        id("grpc")
        id("restate") {
          option("java")
          option("kotlin")
        }
      }
      it.builtins { id("kotlin") }
    }
  }
}

application {
  val mainClassValue: String =
      project.findProperty("mainClass")?.toString() ?: "dev.restate.sdk.examples.Counter"
  mainClass.set(mainClassValue)
}
