plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:8.2.0")
}