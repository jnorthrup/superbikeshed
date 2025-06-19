plugins {
    kotlin("multiplatform") version "2.1.21" // Assuming same Kotlin version as main project
}

group = "borg.trikeshed.consumers.couchdbipfs"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21) // Match main project

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js(IR) {
        browser()
        nodejs()
        binaries.executable()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    // Platform detection for native target (copied from main project)
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs.startsWith("Windows") // More robust check for Windows
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows -> mingwX64()
        // Consider adding a fallback or error if no native target is matched
    }

    sourceSets {
        commonMain.dependencies {
            implementation(kotlin("stdlib-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") // Match main project
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")    // Match main project
            // Dependency on the main Trikeshed project for shared libraries like 'borg.trikeshed.lib' and 'borg.trikeshed.parse.json'
            // This assumes the main Trikeshed project is the root project or provides these.
            // If 'lib' and 'parse.json' are in specific modules, adjust the project path accordingly.
            implementation(rootProject) // Adjust if main project is not root or libs are in a specific module e.g. project(":lib")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlin:kotlin-test-common")
            implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
        }

        jvmMain.dependencies {
            // Specific JVM dependencies from main project if any, e.g. jvm versions of coroutines/datetime
             api("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.2")
             api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
            runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
        }

        jsMain.dependencies {
            implementation(kotlin("stdlib-js"))
            // JS specific versions of coroutines/datetime if needed, often transitive from commonMain
            // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.10.2") // Example if explicit needed
            // implementation("org.jetbrains.kotlinx:kotlinx-datetime-js:0.6.2")      // Example if explicit needed
        }
        // Add other source sets like nativeMain, wasmJsMain if specific dependencies are needed
    }
}

// Compiler options (copied from main project)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xskip-metadata-version-check",
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions",
            "-Xno-source-roots-assertions"
        )
    }
}
