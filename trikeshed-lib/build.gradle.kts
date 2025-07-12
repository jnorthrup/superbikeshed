plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

group = "borg.trikeshed"

kotlin {
    jvm()
    macosArm64()
    linuxX64()
    
    // Configure cinterop for SIMD
    macosArm64 {
        compilations["main"].cinterops {
            val simd by creating {
                defFile = file("src/macosArm64Main/cinterop/simd.def")
                includeDirs("src/macosArm64Main/cinterop/include")
            }
        }
    }
    
    linuxX64 {
        compilations["main"].cinterops {
            val simd by creating {
                defFile = file("src/linuxX64Main/cinterop/simd.def")
                includeDirs("src/linuxX64Main/cinterop/include")
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
} 