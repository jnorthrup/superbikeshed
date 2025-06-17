plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.6.0"
}

kotlin {
    jvm()
    wasmJs { browser() }
    linuxX64() // Also supports macosX64, macosArm64, mingwX64

    sourceSets {
        val commonMain by getting

        // A shared source set for JVM and Native desktop targets
        val desktopMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }

        val jvmMain by getting { dependsOn(desktopMain) }
        
        // Create nativeMain source set
        val nativeMain by creating {
            dependsOn(desktopMain)
        }
        
        val wasmJsMain by getting { dependsOn(commonMain) }
    }
}
