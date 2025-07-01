plugins {
    kotlin("multiplatform") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("com.github.ben-manes.versions") version "0.51.0"
}

group = "rtsgame"
version = "1.0-SNAPSHOT"

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
kotlin {
    jvmToolchain(21)
    
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "rtsgame.js"
                devServer = devServer?.copy(
                    port = 3000,
                    static = mutableListOf(File(projectDir, "src/wasmJsMain/resources").toString())
                )
            }
        }
        binaries.executable()
    }
    
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> macosArm64("native")
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs.startsWith("Linux") -> linuxX64("native")
        hostOs.startsWith("Windows") -> mingwX64("native")
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":Trikeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                // WASM-specific dependencies
            }
        }
        
        val nativeMain by getting {
            dependencies {
                // Native-specific dependencies
            }
        }
    }
}

// Aggressive compilation optimizations
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xskip-prerelease-check",
            "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
            "-Xopt-in=kotlinx.cinterop.ExperimentalForeignApi",
            "-Xopt-in=kotlin.ExperimentalStdlibApi",
            "-Xopt-in=kotlin.time.ExperimentalTime",
            "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
}

// Performance build tasks
tasks.register("buildRelease") {
    dependsOn("wasmJsBrowserProductionWebpack", "jvmJar", "linkReleaseExecutableNative")
    doLast {
        println("Release build complete - all targets built with optimizations")
    }
}

tasks.register("runBenchmark", JavaExec::class) {
    dependsOn("jvmJar")
    classpath = configurations["jvmRuntimeClasspath"] + files("${layout.buildDirectory.get()}/libs")
    mainClass.set("rtsgame.massive.MillionUnitBenchmarkKt")
}

tasks.register("wasmDev") {
    dependsOn("wasmJsBrowserDevelopmentRun")
}

tasks.register("wasmProd") {
    dependsOn("wasmJsBrowserProductionWebpack")
    doLast {
        copy {
            from("${layout.buildDirectory.get()}/dist/wasmJs/productionExecutable")
            into("${layout.buildDirectory.get()}/rtsgame-release")
        }
        copy {
            from("src/wasmJsMain/resources")
            into("${layout.buildDirectory.get()}/rtsgame-release")
        }
        println("Production WASM build ready in build/rtsgame-release/")
    }
}