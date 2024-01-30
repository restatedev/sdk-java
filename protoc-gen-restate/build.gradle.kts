import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  java
  application
  id("com.github.johnrengelman.shadow").version("7.1.2")
  `library-publishing-conventions`
}

description =
    "Protoc plugin to generate interfaces compatible with dev.restate:sdk-api or dev.restate:sdk-api-kotlin"

dependencies {
  compileOnly(coreLibs.javax.annotation.api)
  implementation("com.salesforce.servicelibs:jprotoc:1.2.2") {
    exclude("javax.annotation", "javax.annotation-api")
  }
  implementation(project(":sdk-common"))
}

application { mainClass.set("dev.restate.sdk.gen.RestateGen") }

tasks.named<ShadowJar>("shadowJar") {
  // Override the default jar
  archiveClassifier.set("all")
}
