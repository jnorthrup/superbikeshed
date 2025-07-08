plugins {
    kotlin("jvm") // Version inherited from parent
    application // To build as a CLI application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3") // For JSON output by CLI later
    // No explicit XML parsing library needed for StAX (part of JDK)
    // Dependency on a shared module containing IntelliJProjectDetails would go here.
    // For now, data classes will be duplicated.

    testImplementation(kotlin("test-junit")) // Added for JUnit tests
}

application {
    mainClass.set("nexus.enumerator.intellij.MainKt") // MainKt will be created later for CLI
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
