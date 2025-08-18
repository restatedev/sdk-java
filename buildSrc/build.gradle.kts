plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}


dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.10")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:7.2.1")
}