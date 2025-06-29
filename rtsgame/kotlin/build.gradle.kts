plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        jvmToolchain(21)
    }
<<<<<<< HEAD
    // re-add wasm
=======
    
    js(IR) {
        browser()
        nodejs()
    }
    
>>>>>>> origin/feat/core-serialization-impl
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    when {
        hostOs == "Mac OS X" -> {
            if (hostArch == "aarch64") {
                macosArm64()
            } else {
                macosX64()
            }
        }
        hostOs == "Linux" -> {
            if (hostArch == "aarch64") {
                linuxArm64()
            } else {
                linuxX64()
            }
        }
    }
<<<<<<< HEAD

=======
    
>>>>>>> origin/feat/core-serialization-impl
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${libs.versions.serialization.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${libs.versions.datetime.get()}")
            }
        }
<<<<<<< HEAD

=======
        
>>>>>>> origin/feat/core-serialization-impl
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
<<<<<<< HEAD
    }
}
=======
        
        val jsMain by getting {
            dependencies {
                implementation(kotlin("stdlib-js"))
            }
        }
    }
} 
>>>>>>> origin/feat/core-serialization-impl
