import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.diffplug.spotless")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }

    withJavadocJar()
    withSourcesJar()
}

// Dance to make sure we compile Java 17 compatible bytecode
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
tasks.withType<JavaCompile>().configureEach { options.release = 17 }

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        targetExclude("build/generated/**/*.kt")
        ktfmt()
        licenseHeaderFile("$rootDir/config/license-header")
    }
}