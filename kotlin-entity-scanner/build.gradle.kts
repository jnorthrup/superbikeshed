@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                // Self-contained for now - will integrate with TrikeShed when stable
                // implementation(project(":brokeshed"))
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}