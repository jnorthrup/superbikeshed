plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"


kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    linuxX64()
    js(IR) { // Use the IR compiler
        browser { // Target browser environment
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
        outputModuleName.set("trikeshedCore") // Define a module name for JS
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDirs(
                "src/commonMain/kotlin", // Local sources like borg/trikeshed/core/TrikeShedCore.kt
                "$rootDir/src/commonMain/kotlin/borg/trikeshed/lib" // Shared lib from root project's source tree
            )
            // Excludes for $rootDir paths are not relevant here anymore for these specific srcDirs.
            // Any other excludes for paths *within* these srcDirs could be placed in a nested kotlin {} block.

            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }

        val jvmMain by getting {
            kotlin.srcDir("$rootDir/src/jvmMain/kotlin")
            kotlin {
                exclude("**/QuicCurl.kt")
                exclude("**/QuicMain.kt")
            }
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }

        val linuxX64Main by getting {
            kotlin.srcDir("$rootDir/src/posixMain/kotlin")
            kotlin.srcDir("$rootDir/src/linuxMain/kotlin")
            // No specific native dependencies for coroutines/datetime listed for now,
            // relying on commonMain's. Add if build shows they are needed.
        }

        val jsMain by getting {
            // Point to specific JS sources for trikeshed-core if they exist,
            // and potentially common JS libs if needed by core.
            kotlin.setSrcDirs(files(
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/core/", // If core-specific JS exists
                "$rootDir/src/jsMain/kotlin/borg/trikeshed/lib/",   // If lib has JS parts
                "$rootDir/src/jsMain/kotlin/lib/" // A general lib for JS too
            ))
            kotlin {
                exclude("$rootDir/src/jsMain/kotlin/com/example/trikeshedcore/**")
                exclude("$rootDir/src/jsMain/kotlin/core/**") // Exclude general core JS if it exists
            }
            dependencies {
                implementation(kotlin("stdlib-js"))
                // Add other js-specific dependencies if necessary
            }
        }

        // Define other source sets (nativeMain, etc.) similarly if they should also
        // draw from the root project's structure for this module.
        // For now, focus on common, jvm, linuxX64, js.
    }
}
