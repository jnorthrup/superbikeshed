plugins {
    kotlin("jvm") version "1.8.20"
    // No 'application' plugin needed if this is a library,
    // but keep it if it's meant to be runnable for testing.
    // For now, let's assume it can be a library.
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":Trikeshed")) // Dependency on Trikeshed project

    // Check if Trikeshed's json parsing is self-contained or needs an explicit dependency here
    // For example, if it uses kotlinx.serialization:
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

// Remove the 'application' block if it's not meant to be directly runnable.
// If kept for testing, ensure 'mainClass' is valid or remove it.
