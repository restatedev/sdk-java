plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.diffplug.spotless")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }

    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        targetExclude("build/generated/**/*.kt")
        ktfmt()
        licenseHeaderFile("$rootDir/config/license-header")
    }
}