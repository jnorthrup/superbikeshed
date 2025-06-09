plugins {
    kotlin("js") version "1.9.22"
}

repositories {
    mavenCentral()
}

kotlin {
    js(IR) { // Keep the original js(IR) target if it's used for other purposes
        browser {
            commonWebpackConfig {
                outputFileName = "main.js" // Original output for js(IR)
            }
        }
        binaries.executable()
    }
    wasmJs {
        browser { // This is the main configuration block for wasmJs browser environment
            commonWebpackConfig {
                outputFileName = "Bao-Cline-Kotlin.wasm.js" // Consistent name, .wasm.js indicates it's for WASM
            }
            testTask {
                useKarma {
                    // Example: useChromeHeadless() // Uncomment and configure if needed
                    // Ensure you have karma-chrome-launcher and potentially karma-kotlin-test adapter
                }
            }
        }
        binaries.executable() // Generates the executable distributable for wasmJs
    }
    sourceSets {
        val jsMain by getting { // Dependencies for the js(IR) target
            dependencies {
                implementation(kotlin("stdlib-js"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:1.6.2")
            }
        }
        val wasmJsMain by getting { // Dependencies for the wasmJs target's main source set
            dependencies {
                implementation(kotlin("stdlib-js")) // kotlin-stdlib for JS based targets
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:1.6.2")
            }
        }
        val wasmJsTest by getting { // Dependencies for the wasmJs target's test source set
            dependencies {
                implementation(kotlin("test")) // kotlin.test for common test annotations
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += "-Xexplicit-api=strict"
    }
}

tasks.register("kotlinUpgradeYarnLock") {
    dependsOn(tasks.named("kotlinNpmInstall"))
    doLast {
        rootProject.projectDir.resolve("yarn.lock").takeIf { it.exists() }?.also {
            val lockFileContent = it.readText()
            val updatedContent = lockFileContent.replace(Regex("""(^|"\n)resolved\s+"https://registry.npmjs.org/"""), "$1resolved \"https://registry.yarnpkg.com/")
            if (lockFileContent != updatedContent) {
                it.writeText(updatedContent)
                println("Updated yarn.lock to use yarnpkg.com")
            }
        }
    }
}

// Configure the output directory AND filename for the WASM artifact used by the bridge
tasks.named("wasmJsBrowserProductionWebpack", org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack::class.java) {
    outputFileName = "wasm/Bao-Cline-Kotlin.js" // This is the JS loader file for the WASM binary
    destinationDirectory = project.layout.buildDirectory.dir("produção/kotlin-js-min")
}
