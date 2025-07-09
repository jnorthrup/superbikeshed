plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.2.0"
}

group = "borg.trikeshed"



kotlin {
    jvm()
    
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> {
            macosArm64("macosArm64") {
                compilations.getByName("main") {
                    cinterops {
                        val metal by creating {
                            definitionFile.set(project.file("../gradle/cinterop/metal.def"))
                        }
                        if (project.file("/usr/local/include/mlx").exists() || project.file("/opt/homebrew/include/mlx").exists()) {
                            val mlx by creating {
                                definitionFile.set(project.file("../gradle/cinterop/mlx.def"))
                            }
                        }
                    }
                }
                binaries {
                    executable {
                        entryPoint = "main"
                        linkerOpts += listOf("-framework", "Metal", "-framework", "MetalKit", "-framework", "MetalPerformanceShaders", "-framework", "Accelerate", "-framework", "CoreML")
                        if (project.file("/usr/local/lib/libmlx.dylib").exists() || project.file("/opt/homebrew/lib/libmlx.dylib").exists()) {
                            linkerOpts += listOf("-L/usr/local/lib", "-L/opt/homebrew/lib", "-lmlx")
                        }
                    }
                }
            }
        }
        hostOs == "Mac OS X" -> {
            macosX64("macosX64") {
                compilations.getByName("main") {
                    cinterops {
                        val metal by creating {
                            definitionFile.set(project.file("../gradle/cinterop/metal.def"))
                        }
                    }
                }
                binaries {
                    executable {
                        entryPoint = "main"
                        linkerOpts += listOf("-framework", "Metal", "-framework", "MetalKit", "-framework", "MetalPerformanceShaders", "-framework", "Accelerate", "-framework", "CoreML")
                    }
                }
            }
        }
        hostOs.contains("Windows", ignoreCase = true) -> {
            mingwX64("mingwX64") {
                compilations.getByName("main") {
                    cinterops {
                        if (System.getenv("VULKAN_SDK") != null) {
                            val vulkan by creating {
                                definitionFile.set(project.file("../gradle/cinterop/vulkan.def"))
                            }
                        }
                        if (System.getenv("WEBGPU_SDK") != null) {
                            val webgpu by creating {
                                definitionFile.set(project.file("../gradle/cinterop/webgpu.def"))
                            }
                        }
                    }
                }
                binaries {
                    executable {
                        entryPoint = "main"
                        linkerOpts += listOf("-L${System.getenv("VULKAN_SDK") ?: "C:/VulkanSDK/1.3.268.0"}/Lib", "-lvulkan-1")
                        val webgpuSdk = System.getenv("WEBGPU_SDK") ?: "C:/webgpu"
                        if (project.file("$webgpuSdk/lib").exists()) {
                            linkerOpts += listOf("-L$webgpuSdk/lib", "-lwebgpu")
                        }
                    }
                }
            }
        }
        hostOs == "Linux" && hostArch == "aarch64" -> {
            linuxArm64("linuxArm64") {
                compilations.getByName("main") {
                    cinterops {
                        if (project.file("/usr/include/vulkan").exists()) {
                            val vulkan by creating {
                                definitionFile.set(project.file("../gradle/cinterop/vulkan.def"))
                            }
                        }
                    }
                }
                binaries {
                    executable {
                        entryPoint = "main"
                        linkerOpts += listOf("-lvulkan", "-lpthread", "-ldl")
                    }
                }
            }
        }
        hostOs == "Linux" -> {
            linuxX64("linuxX64") {
                compilations.getByName("main") {
                    cinterops {
                        if (project.file("/usr/include/vulkan").exists()) {
                            val vulkan by creating {
                                definitionFile.set(project.file("../gradle/cinterop/vulkan.def"))
                            }
                        }
                        if (project.file("/usr/local/include/webgpu").exists()) {
                            val webgpu by creating {
                                definitionFile.set(project.file("../gradle/cinterop/webgpu.def"))
                            }
                        }
                    }
                }
                binaries {
                    executable {
                        entryPoint = "main"
                        linkerOpts += listOf("-lvulkan", "-lpthread", "-ldl")
                        if (project.file("/usr/local/lib/libwebgpu.so").exists()) {
                            linkerOpts += listOf("-L/usr/local/lib", "-lwebgpu")
                        }
                    }
                }
            }
        }
    }
    
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":trikeshed-lib"))
                implementation(project(":k2script"))
                implementation(project(":fiduciary"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core")
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
