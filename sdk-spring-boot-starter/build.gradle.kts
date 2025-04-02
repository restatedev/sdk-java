plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
  alias(libs.plugins.spring.dependency.management)
}

description = "Restate SDK Spring Boot starter"

dependencies {
  compileOnly(libs.jspecify)

  val excludeJackson =
      fun ProjectDependency.() {
        // Let spring bring jackson in
        exclude(group = "com.fasterxml.jackson")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.datatype")
      }

  // SDK dependencies
  api(project(":sdk-spring-boot"))
  api(project(":sdk-api"), excludeJackson)
  api(project(":client"), excludeJackson)
  api(project(":sdk-serde-jackson"), excludeJackson)

  // Spring boot starter brought in here for convenience
  api(libs.spring.boot.starter)

  // Testing
  testImplementation(libs.spring.boot.starter.test)
  testAnnotationProcessor(project(":sdk-api-gen"))
  // We need these for the deployment manifest
  testImplementation(project(":sdk-core"))
  testImplementation(libs.jackson.annotations)
  testImplementation(libs.jackson.databind)
  testImplementation(project(":sdk-serde-jackson"))
  testImplementation(project(":sdk-testing"))
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }
