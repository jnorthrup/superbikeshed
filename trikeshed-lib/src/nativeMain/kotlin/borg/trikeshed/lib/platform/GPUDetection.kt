package borg.trikeshed.lib.platform

import kotlinx.cinterop.*
import platform.posix.*

actual object GPUDetection {
    actual fun getAvailableFrameworks(): Set<GPUFramework> {
        val frameworks = mutableSetOf<GPUFramework>()
        val platform = PlatformDetection.getPlatformInfo()
        
        when (platform.os) {
            OperatingSystem.MACOS -> {
                // Metal is always available on macOS
                frameworks.add(GPUFramework.METAL)
                
                // Check for MLX
                if (checkMLXAvailability()) {
                    frameworks.add(GPUFramework.MLX)
                }
                
                // MoltenVK provides Vulkan on macOS
                if (checkVulkanAvailability()) {
                    frameworks.add(GPUFramework.VULKAN)
                }
            }
            
            OperatingSystem.LINUX -> {
                // Check for Vulkan
                if (checkVulkanAvailability()) {
                    frameworks.add(GPUFramework.VULKAN)
                }
                
                // Check for WebGPU
                if (checkWebGPUAvailability()) {
                    frameworks.add(GPUFramework.WEBGPU)
                }
                
                // Check for CUDA
                if (checkCUDAAvailability()) {
                    frameworks.add(GPUFramework.CUDA)
                }
                
                // Check for ROCm
                if (checkROCmAvailability()) {
                    frameworks.add(GPUFramework.ROCM)
                }
                
                // OpenCL is often available
                if (checkOpenCLAvailability()) {
                    frameworks.add(GPUFramework.OPENCL)
                }
            }
            
            OperatingSystem.WINDOWS -> {
                // DirectX 12 on Windows
                frameworks.add(GPUFramework.DIRECTX12)
                
                // Check for Vulkan
                if (checkVulkanAvailability()) {
                    frameworks.add(GPUFramework.VULKAN)
                }
                
                // Check for WebGPU
                if (checkWebGPUAvailability()) {
                    frameworks.add(GPUFramework.WEBGPU)
                }
                
                // Check for CUDA
                if (checkCUDAAvailability()) {
                    frameworks.add(GPUFramework.CUDA)
                }
            }
            
            else -> {
                // Minimal support on other platforms
                if (checkOpenGLAvailability()) {
                    frameworks.add(GPUFramework.OPENGL)
                }
            }
        }
        
        if (frameworks.isEmpty()) {
            frameworks.add(GPUFramework.NONE)
        }
        
        return frameworks
    }
    
    actual fun hasFramework(framework: GPUFramework): Boolean {
        return getAvailableFrameworks().contains(framework)
    }
    
    actual fun getGPUCapabilities(): GPUCapabilities {
        val frameworks = getAvailableFrameworks()
        val devices = detectGPUDevices(frameworks)
        val platform = PlatformDetection.getPlatformInfo()
        
        return GPUCapabilities(
            availableFrameworks = frameworks,
            devices = devices,
            unifiedMemory = platform.os == OperatingSystem.MACOS && 
                           platform.arch == Architecture.ARM64,
            computeCapability = detectComputeCapability(frameworks),
            maxMemoryGB = devices.maxOfOrNull { it.memoryGB } ?: 0f,
            tensorCores = devices.any { it.vendor == GPUVendor.NVIDIA },
            rayTracingCores = frameworks.any { 
                it in setOf(GPUFramework.METAL, GPUFramework.VULKAN, GPUFramework.DIRECTX12)
            },
            neuralCores = platform.os == OperatingSystem.MACOS && 
                         platform.arch == Architecture.ARM64
        )
    }
    
    private fun checkMLXAvailability(): Boolean {
        // Check common MLX installation paths
        val paths = listOf(
            "/usr/local/lib/libmlx.dylib",
            "/opt/homebrew/lib/libmlx.dylib"
        )
        return paths.any { path ->
            access(path, F_OK) == 0
        }
    }
    
    private fun checkVulkanAvailability(): Boolean {
        val platform = PlatformDetection.getPlatformInfo()
        return when (platform.os) {
            OperatingSystem.LINUX -> {
                // Check for Vulkan loader
                access("/usr/lib/libvulkan.so.1", F_OK) == 0 ||
                access("/usr/lib/x86_64-linux-gnu/libvulkan.so.1", F_OK) == 0
            }
            OperatingSystem.WINDOWS -> {
                // Check VULKAN_SDK environment variable
                getenv("VULKAN_SDK") != null
            }
            OperatingSystem.MACOS -> {
                // MoltenVK provides Vulkan on macOS
                access("/usr/local/lib/libMoltenVK.dylib", F_OK) == 0 ||
                access("/opt/homebrew/lib/libMoltenVK.dylib", F_OK) == 0
            }
            else -> false
        }
    }
    
    private fun checkWebGPUAvailability(): Boolean {
        // Check for Dawn or wgpu-native
        val paths = when (PlatformDetection.getPlatformInfo().os) {
            OperatingSystem.LINUX -> listOf(
                "/usr/local/lib/libwebgpu.so",
                "/usr/local/lib/libdawn.so",
                "/usr/local/lib/libwgpu.so"
            )
            OperatingSystem.WINDOWS -> {
                val webgpuSdk = getenv("WEBGPU_SDK")
                if (webgpuSdk != null) {
                    listOf("$webgpuSdk/lib/webgpu.dll")
                } else {
                    emptyList()
                }
            }
            OperatingSystem.MACOS -> listOf(
                "/usr/local/lib/libwebgpu.dylib",
                "/opt/homebrew/lib/libwgpu.dylib"
            )
            else -> emptyList()
        }
        
        return paths.any { path ->
            access(path, F_OK) == 0
        }
    }
    
    private fun checkCUDAAvailability(): Boolean {
        // Check for NVIDIA CUDA
        val platform = PlatformDetection.getPlatformInfo()
        return when (platform.os) {
            OperatingSystem.LINUX -> {
                access("/usr/local/cuda/lib64/libcudart.so", F_OK) == 0
            }
            OperatingSystem.WINDOWS -> {
                getenv("CUDA_PATH") != null
            }
            else -> false
        }
    }
    
    private fun checkROCmAvailability(): Boolean {
        // AMD ROCm is Linux-only
        return PlatformDetection.getPlatformInfo().os == OperatingSystem.LINUX &&
               access("/opt/rocm/lib/libamdhip64.so", F_OK) == 0
    }
    
    private fun checkOpenCLAvailability(): Boolean {
        val platform = PlatformDetection.getPlatformInfo()
        return when (platform.os) {
            OperatingSystem.LINUX -> {
                access("/usr/lib/libOpenCL.so", F_OK) == 0
            }
            OperatingSystem.WINDOWS -> {
                true // Usually available through GPU drivers
            }
            OperatingSystem.MACOS -> {
                true // OpenCL is built into macOS (though deprecated)
            }
            else -> false
        }
    }
    
    private fun checkOpenGLAvailability(): Boolean {
        // OpenGL is generally available on most platforms
        return true
    }
    
    private fun detectGPUDevices(frameworks: Set<GPUFramework>): List<GPUDevice> {
        val devices = mutableListOf<GPUDevice>()
        val platform = PlatformDetection.getPlatformInfo()
        
        // Platform-specific GPU detection
        when (platform.os) {
            OperatingSystem.MACOS -> {
                if (platform.arch == Architecture.ARM64) {
                    // Apple Silicon
                    devices.add(GPUDevice(
                        name = "Apple Silicon GPU",
                        vendor = GPUVendor.APPLE,
                        memoryGB = 8f, // Conservative estimate
                        computeUnits = 8, // M1 base
                        framework = GPUFramework.METAL
                    ))
                }
            }
            else -> {
                // Generic GPU detection for other platforms
                // Would need platform-specific APIs to get actual device info
            }
        }
        
        return devices
    }
    
    private fun detectComputeCapability(frameworks: Set<GPUFramework>): ComputeCapability {
        return when {
            GPUFramework.METAL in frameworks -> {
                val platform = PlatformDetection.getPlatformInfo()
                if (platform.arch == Architecture.ARM64) {
                    ComputeCapability.METAL_3_0 // Apple Silicon supports Metal 3
                } else {
                    ComputeCapability.METAL_1_0
                }
            }
            GPUFramework.VULKAN in frameworks -> ComputeCapability.VULKAN_1_3
            GPUFramework.WEBGPU in frameworks -> ComputeCapability.WEBGPU_1_0
            else -> ComputeCapability.NONE
        }
    }
}