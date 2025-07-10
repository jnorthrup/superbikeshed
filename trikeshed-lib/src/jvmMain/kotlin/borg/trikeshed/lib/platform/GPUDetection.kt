package borg.trikeshed.lib.platform

/**
 * JVM implementation of GPU framework detection
 */
actual object GPUDetection {
    actual fun getAvailableFrameworks(): Set<GPUFramework> {
        val frameworks = mutableSetOf<GPUFramework>()
        
        // Check for OpenGL (available on most JVMs)
        frameworks.add(GPUFramework.OPENGL)
        
        // Check for OpenCL if available
        if (isOpenCLAvailable()) {
            frameworks.add(GPUFramework.OPENCL)
        }
        
        // Check for CUDA if available
        if (isCUDAAvailable()) {
            frameworks.add(GPUFramework.CUDA)
        }
        
        // Check for Vulkan if available
        if (isVulkanAvailable()) {
            frameworks.add(GPUFramework.VULKAN)
        }
        
        return frameworks
    }
    
    actual fun hasFramework(framework: GPUFramework): Boolean {
        return when (framework) {
            GPUFramework.OPENGL -> true // Always available on JVM
            GPUFramework.OPENCL -> isOpenCLAvailable()
            GPUFramework.CUDA -> isCUDAAvailable()
            GPUFramework.VULKAN -> isVulkanAvailable()
            GPUFramework.METAL, GPUFramework.MLX -> false // Not available on JVM
            GPUFramework.DIRECTX12 -> false // Not available on JVM
            GPUFramework.ROCM -> false // Not available on JVM
            GPUFramework.ONEAPI -> false // Not available on JVM
            GPUFramework.WEBGPU -> false // Not available on JVM
            GPUFramework.NONE -> true
        }
    }
    
    actual fun getGPUCapabilities(): GPUCapabilities {
        val frameworks = getAvailableFrameworks()
        val devices = mutableListOf<GPUDevice>()
        
        // Add a default CPU device
        devices.add(GPUDevice(
            name = "JVM CPU",
            vendor = GPUVendor.UNKNOWN,
            memoryGB = (Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0 * 1024.0)).toFloat(),
            computeUnits = Runtime.getRuntime().availableProcessors(),
            framework = GPUFramework.NONE
        ))
        
        return GPUCapabilities(
            availableFrameworks = frameworks,
            devices = devices,
            unifiedMemory = false,
            computeCapability = ComputeCapability.NONE,
            maxMemoryGB = (Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0 * 1024.0)).toFloat(),
            tensorCores = false,
            rayTracingCores = false,
            neuralCores = false
        )
    }
    
    private fun isOpenCLAvailable(): Boolean {
        return try {
            // Try to load OpenCL library
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
    
    private fun isCUDAAvailable(): Boolean {
        return try {
            // Try to load CUDA library
            System.loadLibrary("cuda")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
    
    private fun isVulkanAvailable(): Boolean {
        return try {
            // Try to load Vulkan library
            System.loadLibrary("vulkan")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
} 