plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    jvm()
    wasmJs {
        browser()
        binaries.executable()
    }
    
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
                implementation(project(":Trikeshed"))
                implementation(libs.kotlinx.serialization.core)
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.core)
            }
        }
        
        if (isMac) {
            val macosArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
            
            val macosX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
        
        if (isLinux) {
            val linuxX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
            
            val linuxArm64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
        
        if (isMingwX64) {
            val mingwX64Main by getting {
                dependencies {
                    implementation(kotlin("stdlib"))
                }
            }
        }
    }
}

dependencies {
    add("kspJvm", project(":ksp-processors"))
    add("kspWasmJs", project(":ksp-processors"))
    if (System.getProperty("os.name").startsWith("Mac OS")) {
        add("kspMacosArm64", project(":ksp-processors"))
        add("kspMacosX64", project(":ksp-processors"))
    } else if (System.getProperty("os.name").startsWith("Linux")) {
        add("kspLinuxX64", project(":ksp-processors"))
        add("kspLinuxArm64", project(":ksp-processors"))
    } else if (System.getProperty("os.name").startsWith("Windows")) {
        add("kspMingwX64", project(":ksp-processors"))
    }
}

