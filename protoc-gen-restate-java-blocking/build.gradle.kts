import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  java
  application
  `maven-publish`
  id("com.github.johnrengelman.shadow").version("7.1.2")
}

dependencies {
  compileOnly(coreLibs.javax.annotation.api)
  implementation("com.salesforce.servicelibs:jprotoc:1.2.2") {
    exclude("javax.annotation", "javax.annotation-api")
  }
  implementation(project(":sdk-core"))
}

application { mainClass.set("dev.restate.sdk.blocking.gen.JavaBlockingGen") }

tasks.named<ShadowJar>("shadowJar") {
  // Override the default jar
  archiveClassifier.set("")
}

publishing {
  publications {
    register<MavenPublication>("maven") {
      groupId = "dev.restate.sdk"
      artifactId = "protoc-gen-restate-java-blocking"

      from(components["java"])
    }
  }
}
