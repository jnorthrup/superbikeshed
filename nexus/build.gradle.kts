plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        withJava()
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }
    
    js(IR) {
        browser()
        nodejs()
    }

    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")

    if (hostOs == "Mac OS X") {
        if (hostArch == "aarch64") {
            macosArm64() // Defines macosArm64 target
        } else {
            macosX64()   // Defines macosX64 target
        }
    } else if (hostOs == "Linux") {
        if (hostArch == "aarch64") {
            linuxArm64() // Defines linuxArm64 target
        } else {
            linuxX64()   // Defines linuxX64 target
        }
    }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":trikeshed-core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("khttp:khttp:1.0.0")
                implementation("com.github.docker-java:docker-java-core:3.3.3")
                implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.3")
            }
        }
        
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
            }
        }
        
        jvmTest {
            dependencies {
                implementation(kotlin("test-junit5"))
            }
        }

        // Add native source sets
        val nativeMain by creating {
            dependsOn(commonMain)
        }

        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Linux source sets
        val linuxX64Main by creating {
            dependsOn(nativeMain)
            // Add specific dependencies for linuxX64Main if any
            // e.g. implementation("io.ktor:ktor-client-curl-linuxx64:2.3.7")
        }
        val linuxArm64Main by creating {
            dependsOn(nativeMain)
            // Add specific dependencies for linuxArm64Main if any
            // e.g. implementation("io.ktor:ktor-client-curl-linuxarm64:2.3.7")
        }

        // macOS source sets
        val macosX64Main by creating {
            dependsOn(nativeMain)
            // Add specific dependencies for macosX64Main if any
            // e.g. implementation("io.ktor:ktor-client-darwin-macosx64:2.3.7") // Or ktor-client-curl
        }
        val macosArm64Main by creating {
            dependsOn(nativeMain)
            // Add specific dependencies for macosArm64Main if any
            // e.g. implementation("io.ktor:ktor-client-darwin-macosarm64:2.3.7") // Or ktor-client-curl
        }

        // Corresponding test source sets
        val linuxX64Test by creating {
            dependsOn(nativeTest)
        }
        val linuxArm64Test by creating {
            dependsOn(nativeTest)
        }
        val macosX64Test by creating {
            dependsOn(nativeTest)
        }
        val macosArm64Test by creating {
            dependsOn(nativeTest)
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}