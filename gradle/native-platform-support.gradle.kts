/**
 * Native Platform Support Configuration for MLX, Vulkan, and WebGPU
 * 
 * This file provides shared configuration for native builds across all platforms,
 * ensuring compatibility with GPU computing frameworks.
 */

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * Configures native targets with GPU framework support
 */
fun KotlinNativeTarget.configureGPUSupport() {
    val target = this
    
    compilations.getByName("main") {
        cinterops {
            when (target.name) {
                "macosArm64", "macosX64" -> {
                    // Metal and MLX support for macOS
                    val metal by creating {
                        defFile = project.file("src/nativeInterop/cinterop/metal.def")
                        includeDirs("${System.getenv("DEVELOPER_DIR") ?: "/Applications/Xcode.app/Contents/Developer"}/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/System/Library/Frameworks")
                    }
                    
                    // MLX framework (if available)
                    if (project.file("/usr/local/include/mlx").exists()) {
                        val mlx by creating {
                            defFile = project.file("src/nativeInterop/cinterop/mlx.def")
                            includeDirs("/usr/local/include/mlx", "/opt/homebrew/include/mlx")
                        }
                    }
                }
                
                "mingwX64" -> {
                    // Vulkan and WebGPU support for Windows
                    val vulkan by creating {
                        defFile = project.file("src/nativeInterop/cinterop/vulkan.def")
                        includeDirs("${System.getenv("VULKAN_SDK") ?: "C:/VulkanSDK/1.3.268.0"}/Include")
                    }
                    
                    // WebGPU Dawn or wgpu-native
                    if (project.file("${System.getenv("WEBGPU_SDK") ?: "C:/webgpu"}").exists()) {
                        val webgpu by creating {
                            defFile = project.file("src/nativeInterop/cinterop/webgpu.def")
                            includeDirs("${System.getenv("WEBGPU_SDK") ?: "C:/webgpu"}/include")
                        }
                    }
                }
                
                "linuxX64", "linuxArm64" -> {
                    // Vulkan support for Linux
                    val vulkan by creating {
                        defFile = project.file("src/nativeInterop/cinterop/vulkan.def")
                        includeDirs("/usr/include", "/usr/local/include")
                    }
                    
                    // WebGPU support if available
                    if (project.file("/usr/local/include/webgpu").exists()) {
                        val webgpu by creating {
                            defFile = project.file("src/nativeInterop/cinterop/webgpu.def")
                            includeDirs("/usr/local/include/webgpu")
                        }
                    }
                }
            }
        }
    }
    
    binaries {
        executable {
            entryPoint = "main"
            
            linkerOpts += when (target.name) {
                "macosArm64", "macosX64" -> listOf(
                    "-framework", "Metal",
                    "-framework", "MetalKit",
                    "-framework", "MetalPerformanceShaders",
                    "-framework", "Accelerate",
                    "-framework", "CoreML"
                ).apply {
                    // Add MLX if available
                    if (project.file("/usr/local/lib/libmlx.dylib").exists() ||
                        project.file("/opt/homebrew/lib/libmlx.dylib").exists()) {
                        plus(listOf("-L/usr/local/lib", "-L/opt/homebrew/lib", "-lmlx"))
                    }
                }
                
                "mingwX64" -> listOf(
                    "-L${System.getenv("VULKAN_SDK") ?: "C:/VulkanSDK/1.3.268.0"}/Lib",
                    "-lvulkan-1"
                ).apply {
                    // Add WebGPU if available
                    val webgpuSdk = System.getenv("WEBGPU_SDK") ?: "C:/webgpu"
                    if (project.file("$webgpuSdk/lib").exists()) {
                        plus(listOf("-L$webgpuSdk/lib", "-lwebgpu"))
                    }
                }
                
                "linuxX64", "linuxArm64" -> listOf(
                    "-lvulkan",
                    "-lpthread",
                    "-ldl"
                ).apply {
                    // Add WebGPU if available
                    if (project.file("/usr/local/lib/libwebgpu.so").exists()) {
                        plus(listOf("-L/usr/local/lib", "-lwebgpu"))
                    }
                }
                
                else -> emptyList()
            }
        }
    }
}

/**
 * Extension function to configure all native targets in a multiplatform project
 */
fun org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.configureNativeTargetsWithGPU() {
    val hostOs = System.getProperty("os.name")
    val hostArch = System.getProperty("os.arch")
    
    // Configure targets based on host platform
    when {
        hostOs == "Mac OS X" && hostArch == "aarch64" -> {
            macosArm64().configureGPUSupport()
        }
        hostOs == "Mac OS X" -> {
            macosX64().configureGPUSupport()
        }
        hostOs.contains("Windows", ignoreCase = true) -> {
            mingwX64().configureGPUSupport()
        }
        hostOs == "Linux" && hostArch == "aarch64" -> {
            linuxArm64().configureGPUSupport()
        }
        hostOs == "Linux" -> {
            linuxX64().configureGPUSupport()
        }
    }
}

/**
 * Helper to check for GPU framework availability
 */
fun Project.hasGPUFramework(framework: String): Boolean {
    return when (framework.lowercase()) {
        "metal" -> {
            val os = System.getProperty("os.name")
            os == "Mac OS X"
        }
        "mlx" -> {
            file("/usr/local/include/mlx").exists() || 
            file("/opt/homebrew/include/mlx").exists()
        }
        "vulkan" -> {
            System.getenv("VULKAN_SDK") != null ||
            file("/usr/include/vulkan").exists() ||
            file("C:/VulkanSDK").exists()
        }
        "webgpu" -> {
            System.getenv("WEBGPU_SDK") != null ||
            file("/usr/local/include/webgpu").exists() ||
            file("C:/webgpu").exists()
        }
        else -> false
    }
}