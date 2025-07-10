plugins {
    kotlin("multiplatform")
}

group = "org.k2script.launcher"

kotlin {
    // Native binary targets
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64("native")
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" && hostArch == "aarch64" -> linuxArm64("native")
        hostOs == "Linux" -> linuxX64("native")
        hostOs.contains("Windows", ignoreCase = true) -> mingwX64("native")
        else -> macosX64("native") // fallback
    }
    
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries {
            executable {
                entryPoint = "launcher.main"
                baseName = "k2script-launcher"
            }
        }
    }
    
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation(project(":trikeshed-lib"))
            }
        }
    }
}