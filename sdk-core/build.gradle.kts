import com.google.protobuf.gradle.id

plugins {
  `java-library`
  `library-publishing-conventions`
  id("org.jsonschema2pojo") version "1.2.1"
}

description = "Restate SDK Core"

sourceSets { main { proto { srcDirs("src/main/sdk-proto", "src/main/service-protocol") } } }

dependencies {
  implementation(project(":sdk-common"))

  implementation(coreLibs.protobuf.java)
  implementation(coreLibs.grpc.api)
  implementation(coreLibs.grpc.protobuf)
  implementation(coreLibs.log4j.api)

  // TODO Ideally we don't want this, but we need it for the descriptor...
  implementation(jacksonLibs.jackson.databind)

  // We don't want a hard-dependency on it
  compileOnly(coreLibs.log4j.core)

  implementation(platform(coreLibs.opentelemetry.bom))
  implementation(coreLibs.opentelemetry.api)
  implementation(coreLibs.opentelemetry.semconv)

  testCompileOnly(coreLibs.javax.annotation.api)

  testImplementation(testingLibs.junit.jupiter)
  testImplementation(testingLibs.assertj)
  testImplementation(coreLibs.grpc.stub)
  testImplementation(coreLibs.grpc.protobuf)
  testImplementation(coreLibs.log4j.core)
}

val generatedJ2SPDir = layout.buildDirectory.dir("generated/j2sp")

sourceSets { main { java.srcDir(generatedJ2SPDir) } }

jsonSchema2Pojo {
  setSource(files("$projectDir/src/main/resources/json_schema"))
  targetPackage = "dev.restate.sdk.core.manifest"
  targetDirectory = generatedJ2SPDir.get().asFile

  useLongIntegers = false
  includeSetters = true
  includeGetters = true
  generateBuilders = true
}

tasks {
  withType<JavaCompile> { dependsOn(generateJsonSchema2Pojo) }
  withType<Jar> { dependsOn(generateJsonSchema2Pojo) }
}

protobuf {
  plugins {
    id("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${coreLibs.versions.grpc.get()}" }
  }

  generateProtoTasks { ofSourceSet("test").forEach { it.plugins { id("grpc") } } }
}

// Generate test jar

configurations { register("testArchive") }

tasks.register<Jar>("testJar") {
  archiveClassifier.set("tests")

  from(project.the<SourceSetContainer>()["test"].output)
}

artifacts { add("testArchive", tasks["testJar"]) }
