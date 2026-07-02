plugins {
    java
    id("com.diffplug.spotless")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }

    withJavadocJar()
    withSourcesJar()
}

// Compile with Java 17 bytecode compatibility (also gates the JDK API to 17)
tasks.withType<JavaCompile>().configureEach { options.release = 17 }

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        targetExclude("build/**/*.java")

        googleJavaFormat()

        licenseHeaderFile("$rootDir/config/license-header")
    }
}