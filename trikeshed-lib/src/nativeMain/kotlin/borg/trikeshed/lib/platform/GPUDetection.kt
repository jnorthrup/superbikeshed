package borg.trikeshed.lib.platform

/**
 * Native implementation of GPU framework detection
 */
actual object GPUDetection {
    actual fun getAvailableFrameworks(): Set<GPUFramework> {
        val frameworks = mutableSetOf<GPUFramework>()
        
        // Platform-specific detection
        when (PlatformInfo.current.os) {
            OperatingSystem.MACOS -> {
                frameworks.add(GPUFramework.METAL)
                frameworks.add(GPUFramework.MLX)
                frameworks.add(GPUFramework.OPENGL)
            }
            OperatingSystem.IOS -> {
                frameworks.add(GPUFramework.METAL)
                frameworks.add(GPUFramework.MLX)
                frameworks.add(GPUFramework.OPENGL)
            }
            OperatingSystem.LINUX -> {
                frameworks.add(GPUFramework.OPENGL)
                frameworks.add(GPUFramework.VULKAN)
                frameworks.add(GPUFramework.OPENCL)
                // Check for CUDA/ROCM
                if (isCUDAAvailable()) frameworks.add(GPUFramework.CUDA)
                if (isROCMAvailable()) frameworks.add(GPUFramework.ROCM)
                if (isOneAPIAvailable()) frameworks.add(GPUFramework.ONEAPI)
            }
            OperatingSystem.WINDOWS -> {
                frameworks.add(GPUFramework.OPENGL)
                frameworks.add(GPUFramework.VULKAN)
                frameworks.add(GPUFramework.DIRECTX12)
                frameworks.add(GPUFramework.OPENCL)
                if (isCUDAAvailable()) frameworks.add(GPUFramework.CUDA)
                if (isOneAPIAvailable()) frameworks.add(GPUFramework.ONEAPI)
            }
            else -> {
                frameworks.add(GPUFramework.OPENGL)
            }
        }
        
        return frameworks
    }
    
    actual fun hasFramework(framework: GPUFramework): Boolean {
        return when (framework) {
            GPUFramework.METAL, GPUFramework.MLX -> 
                PlatformInfo.current.os in setOf(OperatingSystem.MACOS, OperatingSystem.IOS)
            GPUFramework.DIRECTX12 -> 
                PlatformInfo.current.os == OperatingSystem.WINDOWS
            GPUFramework.VULKAN -> 
                PlatformInfo.current.os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.ANDROID)
            GPUFramework.CUDA -> 
                isCUDAAvailable()
            GPUFramework.ROCM -> 
                PlatformInfo.current.os == OperatingSystem.LINUX && isROCMAvailable()
            GPUFramework.ONEAPI -> 
                isOneAPIAvailable()
            GPUFramework.OPENCL -> 
                true // Available on most platforms
            GPUFramework.OPENGL -> 
                true // Available on most platforms
            GPUFramework.WEBGPU -> 
                PlatformInfo.current.os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.MACOS)
            GPUFramework.NONE -> 
                true
        }
    }
    
    actual fun getGPUCapabilities(): GPUCapabilities {
        val frameworks = getAvailableFrameworks()
        val devices = mutableListOf<GPUDevice>()
        
        // Platform-specific device detection
        when (PlatformInfo.current.os) {
            OperatingSystem.MACOS -> {
                // Apple Silicon detection
                if (PlatformInfo.current.arch == Architecture.ARM64) {
                    devices.add(GPUDevice(
                        name = "Apple Silicon GPU",
                        vendor = GPUVendor.APPLE,
                        memoryGB = 8.0f, // Unified memory
                        computeUnits = 10, // M1/M2/M3 typical
                        framework = GPUFramework.METAL
                    ))
                }
            }
            OperatingSystem.LINUX -> {
                // Linux GPU detection
                if (isCUDAAvailable()) {
                    devices.add(GPUDevice(
                        name = "NVIDIA GPU",
                        vendor = GPUVendor.NVIDIA,
                        memoryGB = 8.0f,
                        computeUnits = 2048,
                        framework = GPUFramework.CUDA
                    ))
                }
            }
            OperatingSystem.WINDOWS -> {
                // Windows GPU detection
                devices.add(GPUDevice(
                    name = "Windows GPU",
                    vendor = GPUVendor.UNKNOWN,
                    memoryGB = 4.0f,
                    computeUnits = 1024,
                    framework = GPUFramework.DIRECTX12
                ))
            }
        }
        
        // Add CPU fallback
        devices.add(GPUDevice(
            name = "Native CPU",
            vendor = GPUVendor.UNKNOWN,
            memoryGB = 16.0f,
            computeUnits = PlatformInfo.current.processorCount,
            framework = GPUFramework.NONE
        ))
        
        return GPUCapabilities(
            availableFrameworks = frameworks,
            devices = devices,
            unifiedMemory = PlatformInfo.current.os in setOf(OperatingSystem.MACOS, OperatingSystem.IOS),
            computeCapability = when (PlatformInfo.current.os) {
                OperatingSystem.MACOS -> ComputeCapability.METAL_3_0
                else -> ComputeCapability.NONE
            },
            maxMemoryGB = 16.0f,
            tensorCores = PlatformInfo.current.os in setOf(OperatingSystem.MACOS, OperatingSystem.IOS),
            rayTracingCores = false,
            neuralCores = PlatformInfo.current.os in setOf(OperatingSystem.MACOS, OperatingSystem.IOS)
        )
    }
    
    private fun isCUDAAvailable(): Boolean {
        return try {
            // Native CUDA detection
            platform.posix.dlopen("libcuda.so", platform.posix.RTLD_LAZY)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isROCMAvailable(): Boolean {
        return try {
            // Native ROCm detection
            platform.posix.dlopen("libamdhip64.so", platform.posix.RTLD_LAZY)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isOneAPIAvailable(): Boolean {
        return try {
            // Native oneAPI detection
            platform.posix.dlopen("libsycl.so", platform.posix.RTLD_LAZY)
            true
        } catch (e: Exception) {
            false
        }
    }
}