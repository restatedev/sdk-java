plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    // Pin to a version that is also supported by Kotlin
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
}


dependencies {
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.0.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.0")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.0.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
}