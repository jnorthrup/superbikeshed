plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
}

group = "borg.trikeshed"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> {
            macosArm64("macosArm64") {
                binaries {
                    executable {
                        entryPoint = "main"
                        baseName = "mcp-native-host"
                    }
                    
                    // Test executable
                    executable("test") {
                        entryPoint = "main"
                        baseName = "mcp-native-test"
                        compilation = compilations["test"]
                    }
                }
            }
        }
        hostOs == "Mac OS X" -> {
            macosX64("macosX64") {
                binaries {
                    executable {
                        entryPoint = "main"
                        baseName = "mcp-native-host"
                    }
                }
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        // JVM-specific dependencies only
        val jvmMain by getting {
            dependencies {
                // Use the working KMP uring module
                implementation(project(":trikeshed-uring"))
                implementation(kotlin("stdlib"))
            }
        }
        
        // Native sources without JVM dependencies
        val nativeMain by creating
        
        val nativeTest by creating
        
        
    }
}

// Simple launcher task (working)
tasks.register<JavaExec>("run") {
    mainClass.set("borg.trikeshed.launcher.SimpleLauncher")
    classpath = sourceSets["jvmMain"].runtimeClasspath
    
    jvmArgs = listOf(
        "-Xmx2g",
        "-XX:+UseG1GC",
        "-Dfile.encoding=UTF-8"
    )
    
    standardInput = System.`in`
}

tasks.register<JavaExec>("runFiduciary") {
    mainClass.set("borg.trikeshed.launcher.FiduciaryBlobstoreLauncher")
    classpath = sourceSets["jvmMain"].runtimeClasspath
    
    jvmArgs = listOf(
        "-Xmx4g",
        "-XX:+UseG1GC",
        "-Dfile.encoding=UTF-8",
        "-Dfiduciary.mode=production"
    )
    
    standardInput = System.`in`
}