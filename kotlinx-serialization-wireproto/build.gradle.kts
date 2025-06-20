plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.google.devtools.ksp") version "2.1.21-2.0.2"
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)
    
    jvm()
    js(IR) {
        browser()
        nodejs()
    }
    macosArm64()
    linuxX64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":brokeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
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
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        
        val macosArm64Main by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        
        val linuxX64Main by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}

dependencies {
    add("kspJvm", project(":ksp-processors"))
    add("kspJs", project(":ksp-processors"))
    add("kspMacosArm64", project(":ksp-processors"))
    add("kspLinuxX64", project(":ksp-processors"))
}

