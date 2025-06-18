plugins {
    kotlin("multiplatform")
    id("com.github.ben-manes.versions") version "0.51.0"
}

kotlin {
    jvm()
    wasmJs {
        browser()
        nodejs()
    }
    
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
                // TODO: Re-enable when Trikeshed builds
                // implementation(project(":Trikeshed"))
                implementation("com.rtsgame:shared:1.0.0")
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        
        val wasmJsMain by getting {
            dependencies {
                // WASM JS-specific dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.0.21")
                implementation("org.jetbrains.kotlinx:kotlinx-html-js:0.9.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-browser-wasm:0.0.21")
                implementation("org.jetbrains.kotlinx:kotlinx-js:0.0.21")
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