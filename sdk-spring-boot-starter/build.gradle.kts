plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
  alias(libs.plugins.spring.dependency.management)
}

description = "Restate SDK Spring Boot starter"

dependencies {
  compileOnly(libs.jspecify)

  api(project(":sdk-spring-boot"))
  api(project(":sdk-api")) {
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
  api(project(":sdk-serde-jackson")) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  // We need these for the deployment manifest
  testImplementation(project(":sdk-core"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)

  testAnnotationProcessor(project(":sdk-api-gen"))
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(project(":sdk-testing"))

  testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
