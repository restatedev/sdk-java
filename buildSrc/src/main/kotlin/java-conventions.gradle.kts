
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone")
    id("com.diffplug.spotless")
}

dependencies { errorprone("com.google.errorprone:error_prone_core:2.28.0") }

// Configure the java toolchain to use. If not found, it will be downloaded automatically
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }

    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disableWarningsInGeneratedCode.set(true)
    options.errorprone.disable(
        // We use toString() in proto messages for debugging reasons.
        "LiteProtoToString",
        // This check is proposing to use a guava API instead...
        "StringSplitter",
        // This is conflicting with a javadoc warn lint
        "MissingSummary"
    )
    options.errorprone.excludedPaths.set(".*/build/generated/.*")
}

tasks.withType<Test> { useJUnitPlatform() }

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        targetExclude("build/**/*.java")

        googleJavaFormat()

        licenseHeaderFile("$rootDir/config/license-header")
    }
}