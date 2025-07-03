@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.rtsgame"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()
    wasmJs { 
        browser()
        nodejs()
        binaries.executable()
    }
    
    // Native targets based on host OS
    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> {
            macosX64()
            macosArm64()
        }
        hostOs == "Linux" -> {
            linuxX64()
            linuxArm64()
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":trikeshed-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation(npm("source-map-support", "0.5.21"))
                // Stanford CoreNLP for NLP processing (https://stanfordnlp.github.io/CoreNLP/)
                implementation("edu.stanford.nlp:stanford-corenlp:4.5.10")
                implementation("edu.stanford.nlp:stanford-corenlp:4.5.10:models")
                implementation("edu.stanford.nlp:stanford-corenlp:4.5.10:models-english")
                // Optionally, add models for other languages if needed
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmTest {
            // Removed explicit dependency on kotlin("test-junit5") to resolve conflict
        }
    }
}