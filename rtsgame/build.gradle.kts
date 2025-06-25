plugins {
    kotlin("multiplatform") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

kotlin {
    js(IR) {
        browser()
        binaries.executable()
    }
    jvm()
    // re-add wasm
    // Platform detection for native targets
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    val isMacOS = hostOs == "Mac OS X"
    val isLinux = hostOs == "Linux"
    val isWindows = hostOs == "Windows"
    val isArm64 = hostArch == "aarch64" || hostArch == "arm64"

    when {
        isMacOS && isArm64 -> macosArm64()
        isMacOS -> macosX64()
        isLinux && isArm64 -> linuxArm64()
        isLinux -> linuxX64()
        isWindows -> mingwX64()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // implementation(project(":Trikeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
    }
}

tasks {
    register("buildAll") {
        dependsOn("build")
        dependsOn("wasmJsBrowserProductionWebpack")
    }

    register("runJvm", JavaExec::class) {
        classpath = configurations["jvmRuntimeClasspath"] + files("${layout.buildDirectory.get()}/classes/kotlin/jvm/main")
        mainClass.set("rtsgame.MainJvmKt")
        dependsOn("jvmMainClasses")
    }

    register("runWasm") {
        dependsOn("wasmJsBrowserDevelopmentRun")
    }

    // Clean task to remove all build artifacts
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${layout.buildDirectory.get()}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
            delete("${project.projectDir}/js")
            delete("${project.projectDir}/node_modules")
        }
    }
}
