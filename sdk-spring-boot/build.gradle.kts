plugins {
  `java-conventions`
  `java-library`
  `test-jar-conventions`
  `library-publishing-conventions`
  alias(libs.plugins.spring.dependency.management)
}

description = "Restate SDK Spring Boot integration"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-common")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  api(project(":client")) {
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
  implementation(libs.vertx.core) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  implementation(libs.spring.boot.starter)

  // Spring is going to bring jackson in with this
  implementation(libs.spring.boot.starter.json)

  testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
