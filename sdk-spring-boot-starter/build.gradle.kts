plugins {
  `java-conventions`
  `java-library`
  `test-jar-conventions`
  `library-publishing-conventions`
  id("io.spring.dependency-management") version "1.1.6"
}

description = "Restate SDK Spring Boot starter"

val springBootVersion = "3.3.5"

java { toolchain { languageVersion = JavaLanguageVersion.of(17) } }

dependencies {
  compileOnly(coreLibs.jspecify)

  api(project(":sdk-common")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }
  api(project(":sdk-api")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }
  api(project(":sdk-serde-jackson")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  implementation(project(":sdk-http-vertx")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }
  implementation(project(":sdk-request-identity"))
  implementation(platform(vertxLibs.vertx.bom)) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }
  implementation(vertxLibs.vertx.core) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")

  // Spring is going to bring jackson in with this
  implementation("org.springframework.boot:spring-boot-starter-json:$springBootVersion")

  // We need these for the deployment manifest
  testImplementation(project(":sdk-core"))
  testImplementation(platform(jacksonLibs.jackson.bom))
  testImplementation(jacksonLibs.jackson.annotations)
  testImplementation(jacksonLibs.jackson.databind)

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-serde-jackson"))

  testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
