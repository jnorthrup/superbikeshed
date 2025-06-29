plugins {
<<<<<<< HEAD
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
=======
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
>>>>>>> origin/feat/core-serialization-impl
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
<<<<<<< HEAD

=======
    
>>>>>>> origin/feat/core-serialization-impl
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }
<<<<<<< HEAD

    wasmJs {
        browser()
        binaries.executable()
    }

=======
    
    js(IR) {
        browser()
        nodejs()
        binaries.executable()
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

    sourceSets {
        val commonMain by getting {
            dependencies {
<<<<<<< HEAD
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
            }
        }

=======
                implementation(project(":Trikeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
            }
        }
        
>>>>>>> origin/feat/core-serialization-impl
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlin:kotlin-test-common")
            implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        }
<<<<<<< HEAD

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.junit.jupiter.api)
            runtimeOnly(libs.junit.jupiter.engine)
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }

=======
        
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
        }

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
<<<<<<< HEAD
}
=======
}
>>>>>>> origin/feat/core-serialization-impl
