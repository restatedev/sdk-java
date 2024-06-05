plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.diffplug.spotless")
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        targetExclude("build/generated/**/*.kt")
        ktfmt()
        licenseHeaderFile("$rootDir/config/license-header")
    }
}