plugins {
    kotlin("multiplatform") version "2.2.0-RC2"
}


group = "borg.trikeshed"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()
    js {
        browser()
        nodejs()
    }
    targets.all {
        withSourcesJar()
    }

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")

    when {
        hostOs == "Mac OS X" -> {
            if (hostArch == "aarch64") {
                macosArm64("native")
            } else {
                macosX64("native")
            }
        }
        hostOs == "Linux" -> {
            if (hostArch == "aarch64") {
                linuxArm64("native")
            } else {
                linuxX64("native")
            }
        }
        // We are not explicitly asked to handle Windows native here,
        // but if it were, it would be:
        // hostOs.startsWith("Windows") -> mingwX64("native")
        // else -> throw GradleException("Host OS is not supported for native compilation: $hostOs, $hostArch")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation(project(":Review:trikeshed-core"))
            }
            kotlin {
                // Default srcDir is usually src/commonMain/kotlin
                // exclude("borg/trikeshed/core/**") // Now compiled by :trikeshed-core
                // exclude("borg/trikeshed/lib/**")   // Now compiled by :trikeshed-core
                exclude("borg/trikeshed/net/**")
                exclude("evolution/**")
                exclude("com/example/trikeshedcore/**")
                exclude("gk/kademlia/**")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }

        // Generic native source sets if they don't exist
        val nativeMain by creating {
            dependsOn(commonMain)
            // If common native code exists, point to its directory, e.g. src/nativeMain/kotlin
        }
        val nativeTest by creating {
            dependsOn(commonTest)
            // If common native tests exist, point to its directory, e.g. src/nativeTest/kotlin
        }

        val linuxX64Main by getting {
            dependsOn(nativeMain) // Depend on the generic nativeMain
            kotlin.srcDirs("src/posixMain/kotlin", "src/linuxX64Main/kotlin")
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-linuxx64:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-linuxx64:0.6.1")
            }
        }
        // Add corresponding source sets for other native targets
        // Ensure these new source sets also depend on nativeMain or commonMain as appropriate.
        // If specific dependencies are needed for arm64, they should be added here.
        // For now, assume they share dependencies with nativeMain or posix sources.

        val linuxArm64Main by creating {
            dependsOn(nativeMain)
            kotlin.srcDirs("src/posixMain/kotlin", "src/linuxArm64Main/kotlin") // Or just src/posixMain/kotlin if no arm-specific linux code
             dependencies {
                // Add arm64 specific dependencies if any, or use common native dependencies
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-linuxarm64:1.9.0") // Example, check actual artifact
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-linuxarm64:0.6.1") // Example, check actual artifact
            }
        }
        val macosX64Main by creating {
            dependsOn(nativeMain)
            kotlin.srcDirs("src/posixMain/kotlin", "src/macosX64Main/kotlin") // Or just src/posixMain/kotlin
            dependencies {
                 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-macosx64:1.9.0")
                 implementation("org.jetbrains.kotlinx:kotlinx-datetime-macosx64:0.6.1")
            }
        }
        val macosArm64Main by creating {
            dependsOn(nativeMain)
            kotlin.srcDirs("src/posixMain/kotlin", "src/macosArm64Main/kotlin") // Or just src/posixMain/kotlin
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-macosarm64:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime-macosarm64:0.6.1")
            }
        }
        // Add corresponding test source sets if needed, e.g., linuxArm64Test, macosX64Test, macosArm64Test
        // These would depend on nativeTest.
    }
}
