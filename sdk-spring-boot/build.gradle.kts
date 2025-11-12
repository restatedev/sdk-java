plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "Restate SDK Spring Boot integration"

dependencies {
  compileOnly(libs.jspecify)

  val excludeJackson =
      fun ProjectDependency.() {
        // Let spring bring jackson in
        exclude(group = "com.fasterxml.jackson")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson.datatype")
      }

  // SDK deps
  implementation(project(":sdk-common"), excludeJackson)
  implementation(project(":sdk-core"), excludeJackson)
  implementation(project(":client"), excludeJackson)
  implementation(project(":sdk-request-identity"), excludeJackson)

  // Optional dependency - only needed if user configures a separate port for Restate endpoint
  compileOnly(project(":sdk-http-vertx"), excludeJackson)
  compileOnly(libs.vertx.core) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  implementation(libs.spring.boot)
  implementation(libs.spring.web)
  implementation(libs.spring.webflux)
  implementation(libs.spring.boot.starter.logging)
  // In principle kotlin won't need this, but it's needed by the SDK core anyway, so we just import
  // this in.
  implementation(libs.spring.boot.starter.json)

  testImplementation(libs.spring.boot.starter)
  testImplementation(libs.spring.boot.starter.json)
  testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }

tasks.withType<Javadoc> { isFailOnError = false }
