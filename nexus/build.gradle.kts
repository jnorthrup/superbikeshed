plugins {
    kotlin("multiplatform") version "2.1.21"
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
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
        val commonMain by getting {
            dependencies {
                implementation(project(":Trikeshed"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("khttp:khttp:1.0.0")
                implementation("com.github.docker-java:docker-java-core:3.3.3")
                implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.3")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.3")
            }
        }
        
        val jvmTest by getting {
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
        }
        val linuxArm64Main by creating {
            dependsOn(nativeMain)
        }

        // macOS source sets
        val macosX64Main by creating {
            dependsOn(nativeMain)
        }
        val macosArm64Main by creating {
            dependsOn(nativeMain)
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