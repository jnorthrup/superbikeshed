plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.6.0" // Ensure this version is compatible with root project
}

kotlin {
    jvm {
        jvmToolchain(21) // Ensure consistent with root project
        withJava()
    }
    
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    val nativeTargets = mutableListOf<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>()

    // Configure native targets based on host capabilities
    // Focusing on macOS and Linux as per TODO
    if (isMacOS) {
        if (isArm64) nativeTargets.add(macosArm64()) else nativeTargets.add(macosX64())
    } else if (isLinux) {
        if (isArm64) nativeTargets.add(linuxArm64()) else nativeTargets.add(linuxX64())
    }
    // Ensure at least one target is configured for headless environments or other OS.
    // If no specific host matches, configure a common one like linuxX64 as a fallback for cinterop setup.
    if (nativeTargets.isEmpty()) {
        println("BoingDemo: No specific native target for host $hostOs $hostArch. Adding linuxX64 as a default for cinterop configuration.")
        nativeTargets.add(linuxX64())
    }


    nativeTargets.forEach { target ->
        target.compilations.getByName("main") {
            cinterops.create("miniaudio") {
                defFile("src/nativeMain/cinterop/miniaudio/miniaudio.def")
                packageName("thirdparty.miniaudio")
                // Attempt to define MA_IMPLEMENTATION here. If this fails during the actual build,
                // a separate .c file including miniaudio.h with #define MA_IMPLEMENTATION
                // and compiling it via nativeLink will be the next step.
                compilerOpts("-DMA_IMPLEMENTATION")
                // MA_NO_JACK is in .def file for Linux (compilerOpts.linux in .def will be merged)
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.ui)
            }
        }

        jvmMain { // Renamed from desktopMain for clarity if it's just for JVM
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.desktop.currentOs) // For Skia/Compose Desktop
            }
        }

        // This is the sourceSet for desktop specific code (like main runner)
        // It was previously named desktopMain in the ls output and original build file reading
        // Let's ensure we have a source set that maps to src/desktopMain/kotlin
        val desktopMain by creating { // This ensures the desktopMain source set exists
             dependsOn(commonMain.get())
             dependencies {
                implementation(compose.desktop.currentOs)
             }
        }


        val nativeMain by creating {
            dependsOn(commonMain.get())
            // This source set is for common native code, including the actual playSound
        }

        // Wire native target main source sets to depend on nativeMain
        nativeTargets.forEach { target ->
            target.compilations.getByName("main").defaultSourceSet.dependsOn(nativeMain)
        }

        // Ensure desktop (JVM) sources are correctly associated if not covered by jvmMain alone
        // Based on original structure, compose.desktop block implies a jvm() target.
        // The DesktopCanvas.kt was in src/desktopMain/kotlin
        // The jvm() target's main compilation usually uses src/jvmMain/kotlin
        // If DesktopCanvas.kt is intended for the JVM run, jvmMain should contain it or depend on a set that does.
        // The existing `mainClass` points to `com.example.boingdemo.DesktopCanvasKt`.
        // We need to ensure DesktopCanvas.kt is part of the jvmMain compilation.
        // If `desktopMain` sourceSet is separate from `jvmMain`, then `jvmMain` might need to depend on `desktopMain`.
        // Given the original script, `desktopMain` was used for compose.desktop dependencies.
        // Corrected: `nativeMain` depends on `commonMain`.
        // `jvmMain` is the standard for JVM code. If `DesktopCanvasKt` is in `src/desktopMain/kotlin`,
        // we need to tell `jvmMain` to use it or rename/move files.
        // For now, assume `DesktopCanvasKt` is in `src/jvmMain/kotlin` or `src/desktopMain/kotlin` and `jvmMain` includes it.
        // The `val desktopMain by creating` block should handle `src/desktopMain/kotlin`.
        // We need to make sure `jvm()` target uses it.
        getByName("jvmMain").dependsOn(desktopMain) // Explicitly make jvmMain depend on desktopMain if they are separate entities

    }
}

compose.desktop {
    application {
        // This class must be in a source set compiled for the JVM target (e.g., jvmMain or desktopMain)
        mainClass = "com.example.boingdemo.DesktopCanvasKt"
    }
}
