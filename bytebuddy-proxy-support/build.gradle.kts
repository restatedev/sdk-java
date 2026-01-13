plugins {
  `java-conventions`
  `kotlin-conventions`
  `java-library`
  `library-publishing-conventions`
}

description = "ByteBuddy proxy support"

dependencies {
  compileOnly(libs.jspecify)

  implementation(project(":common"))
  implementation(libs.bytebuddy)
  implementation(libs.objenesis)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.assertj)
}

tasks.withType<Javadoc> { isFailOnError = false }
