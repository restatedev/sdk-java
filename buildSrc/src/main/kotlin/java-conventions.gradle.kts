plugins {
    java
    id("com.diffplug.spotless")
}

// Configure the java toolchain to use. If not found, it will be downloaded automatically
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }

    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        targetExclude("build/**/*.java")

        googleJavaFormat()

        licenseHeaderFile("$rootDir/config/license-header")
    }
}