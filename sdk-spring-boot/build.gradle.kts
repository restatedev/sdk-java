plugins {
  `java-conventions`
  `java-library`
  `library-publishing-conventions`
  alias(libs.plugins.spring.dependency.management)
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
  implementation(project(":client"), excludeJackson)
  implementation(project(":sdk-http-vertx"), excludeJackson)
  implementation(project(":sdk-request-identity"), excludeJackson)
  implementation(libs.vertx.core) {
    // Let spring bring jackson in
    exclude(group = "com.fasterxml.jackson")
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.datatype")
  }

  implementation(libs.spring.boot)
  implementation(libs.spring.boot.starter.logging)
  // In principle kotlin won't need this, but it's needed by the SDK core anyway, so we just import
  // this in.
  implementation(libs.spring.boot.starter.json)

  testImplementation(libs.spring.boot.starter)
  testImplementation(libs.spring.boot.starter.json)
  testImplementation(libs.spring.boot.starter.test)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.compilerArgs.add("-parameters") }

tasks.withType<Javadoc> { isFailOnError = false }
