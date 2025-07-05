@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.rtsgame"

kotlin {
    jvm {
        compilations["main"].defaultSourceSet {
            kotlin.srcDir("src/main/kotlin")
        }
        // withJava() deprecated in Kotlin 2.2.0 - Java sources are automatically configured
        // Set the main class for the JVM application plugin
        tasks.withType<Jar> {
            manifest {
                attributes["Main-Class"] = "k2script.K2scriptKt"
            }
        }
    }
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
                implementation(project(":trikeshed-io"))
                implementation(project(":trikeshed-cursor"))
                implementation(project(":trikeshed-isam"))
                implementation(project(":trikeshed-net"))
                implementation(project(":trikeshed-strace"))
                implementation(project(":trikeshed-torrent"))
                implementation(project(":trikeshed-reactor"))
                implementation(project(":trikeshed-services"))
                implementation(project(":trikeshed-ljson"))
                implementation(project(":trikeshed-dht"))
                implementation(project(":trikeshed-couchdb"))
                implementation(project(":trikeshed-ipc"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation(npm("source-map-support", "0.5.21"))
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmTest {
            dependencies {
// Removed explicit dependency on kotlin("test-junit5") to resolve conflict
            }
        }
        jvmMain {
            dependencies {
                implementation("commons-cli:commons-cli:1.5.0")
                implementation("commons-io:commons-io:2.15.1")
                implementation("commons-codec:commons-codec:1.16.1")
                implementation("org.apache.commons:commons-lang3:3.14.0")
                implementation("com.vdurmont:semver4j:3.1.0")
            }
        }
    }
}