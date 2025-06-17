plugins {
    kotlin("multiplatform") version "1.9.22"
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
        val nativeMain by getting {
            dependsOn(desktopMain)
            // For native targets, the entry point is usually defined per target.
            // The provided example has a main in desktopMain, which works for JVM.
            // For native, we might need to ensure an entry point is correctly set up
            // or call the desktopMain's main from each native target's main.
            // For now, assume desktopMain's main will be accessible or adapted.
        }
        val wasmJsMain by getting { dependsOn(commonMain) }
    }
}
