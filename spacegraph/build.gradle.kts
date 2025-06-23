plugins {
    kotlin("multiplatform")
    id("com.github.ben-manes.versions")
}

kotlin {
    jvmToolchain(21)
    jvm {
        // jvmToolchain(21) removed from here
    }
    
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
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${libs.versions.coroutines.get()}")
            }
        }
    }
}
//re-add wasm
tasks {
    register("buildAll") {
        dependsOn("build")
    }
    
    
    register("cleanAll") {
        dependsOn("clean")
        doLast {
            delete("${layout.buildDirectory.get()}")
            delete("${project.projectDir}/build")
            delete("${project.projectDir}/dist")
            delete("${project.projectDir}/node_modules")
        }
    }
} 