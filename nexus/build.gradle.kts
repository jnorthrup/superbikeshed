@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.rtsgame"

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
    }
}

tasks.withType<JavaExec> {
    mainClass.set(System.getProperty("mainClass", "nexus.Main"))
}

tasks {
    // Create executable jar
    register<Jar>("executableJar") {
        dependsOn("jvmJar")
        archiveClassifier.set("executable")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        
        manifest {
            attributes["Main-Class"] = "nexus.agent.NvidiaTaskerMain"
        }
        
        from(sourceSets["jvmMain"].output)
        
        // Include dependencies
        from(configurations["jvmRuntimeClasspath"].map { 
            if (it.isDirectory) it else zipTree(it) 
        })
    }
}