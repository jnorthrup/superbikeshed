plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    
    
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.json:json:20231013")
                // Remove Maven dependencies for now - not needed for basic functionality
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveBaseName.set("nexus-all")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}

