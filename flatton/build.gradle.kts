plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
=======
    kotlin("multiplatform") version "2.1.21"
>>>>>>> origin/feat/core-serialization-impl
    `maven-publish`
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
<<<<<<< HEAD

    jvm()
    wasmJs {
        browser()
        binaries.executable()
    }

=======
    
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    
>>>>>>> origin/feat/core-serialization-impl
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val isMac = hostOs.startsWith("Mac OS")
    val isLinux = hostOs.startsWith("Linux")

    if (isMac) {
        macosArm64()
        macosX64()
    } else if (isLinux) {
        linuxX64()
        linuxArm64()
    } else if (isMingwX64) {
        mingwX64()
    }
<<<<<<< HEAD

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kotlinx-serialization-scanner"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

=======
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":Trikeshed"))
                implementation(project(":kotlinx-serialization-scanner"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
<<<<<<< HEAD

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

=======
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        if (isMac) {
            val macosArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
<<<<<<< HEAD

=======
            
>>>>>>> origin/feat/core-serialization-impl
            val macosX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
<<<<<<< HEAD

=======
        
>>>>>>> origin/feat/core-serialization-impl
        if (isLinux) {
            val linuxX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
<<<<<<< HEAD

=======
            
>>>>>>> origin/feat/core-serialization-impl
            val linuxArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
<<<<<<< HEAD

=======
        
>>>>>>> origin/feat/core-serialization-impl
        if (isMingwX64) {
            val mingwX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
    }
}

// Disable linting to keep code terse
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
<<<<<<< HEAD
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
        )
    }
}
=======
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi"
        )
    }
} 
>>>>>>> origin/feat/core-serialization-impl
