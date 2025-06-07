plugins {
    kotlin("multiplatform")
}

group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

repositories { // Ensure repositories are here if not inherited from root
    mavenCentral()
    google()
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
    linuxX64()
    js(IR) { browser() } // JS target can be added later

    sourceSets {
        val commonMain by getting {
            // Include all sources from borg/trikeshed/core and borg/trikeshed/lib
            kotlin.srcDirs(
                "$rootDir/src/commonMain/kotlin/borg/trikeshed/core",
                "$rootDir/src/commonMain/kotlin/borg/trikeshed/lib"
            )
            // Exclude conflicting general 'core' and 'com/example/trikeshedcore' from this module's compilation
            // These paths are relative to $rootDir/src/commonMain/kotlin, so they should be fine as they are
            // not under borg/trikeshed/core or borg/trikeshed/lib which are now the source dirs.
            // However, to be safe and ensure clarity, if these are meant to be excluded from the root,
            // they should be in the root build.gradle.kts. Let's assume they are for any other sources
            // that might accidentally be picked up if srcDirs was broader. Given the new specific srcDirs,
            // these excludes might not be strictly necessary here anymore but are harmless.
            kotlin {
                exclude("$rootDir/src/commonMain/kotlin/core/**") // This effectively means these paths won't be included if they aren't under the specified srcDirs.
                exclude("$rootDir/src/commonMain/kotlin/com/example/trikeshedcore/**")
            }

            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
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
