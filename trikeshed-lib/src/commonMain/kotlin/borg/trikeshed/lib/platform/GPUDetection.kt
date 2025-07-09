package borg.trikeshed.lib.platform

/**
 * GPU framework detection and capabilities
 */
expect object GPUDetection {
    fun getAvailableFrameworks(): Set<GPUFramework>
    fun hasFramework(framework: GPUFramework): Boolean
    fun getGPUCapabilities(): GPUCapabilities
}

enum class GPUFramework {
    METAL,           // Apple Metal API
    MLX,             // Apple MLX ML framework
    VULKAN,          // Khronos Vulkan API
    WEBGPU,          // W3C WebGPU API
    CUDA,            // NVIDIA CUDA
    ROCM,            // AMD ROCm
    ONEAPI,          // Intel oneAPI
    DIRECTX12,       // Microsoft DirectX 12
    OPENCL,          // OpenCL
    OPENGL,          // OpenGL/OpenGL ES
    NONE             // No GPU acceleration
}

data class GPUCapabilities(
    val availableFrameworks: Set<GPUFramework>,
    val devices: List<GPUDevice>,
    val unifiedMemory: Boolean,
    val computeCapability: ComputeCapability,
    val maxMemoryGB: Float,
    val tensorCores: Boolean,
    val rayTracingCores: Boolean,
    val neuralCores: Boolean  // Apple Neural Engine, Intel NPU, etc.
)

data class GPUDevice(
    val name: String,
    val vendor: GPUVendor,
    val memoryGB: Float,
    val computeUnits: Int,
    val framework: GPUFramework
)

enum class GPUVendor {
    APPLE,
    NVIDIA,
    AMD,
    INTEL,
    QUALCOMM,
    MALI,
    UNKNOWN
}

data class ComputeCapability(
    val major: Int,
    val minor: Int
) : Comparable<ComputeCapability> {
    override fun compareTo(other: ComputeCapability): Int {
        return when {
            major != other.major -> major - other.major
            else -> minor - other.minor
        }
    }
    
    companion object {
        val NONE = ComputeCapability(0, 0)
        
        // Common capability levels
        val METAL_1_0 = ComputeCapability(1, 0)
        val METAL_3_0 = ComputeCapability(3, 0)  // M1/M2/M3
        val VULKAN_1_0 = ComputeCapability(1, 0)
        val VULKAN_1_3 = ComputeCapability(1, 3)
        val WEBGPU_1_0 = ComputeCapability(1, 0)
    }
}

/**
 * Helper functions for GPU framework detection
 */
fun PlatformInfo.supportsGPUFramework(framework: GPUFramework): Boolean {
    return when (framework) {
        GPUFramework.METAL, GPUFramework.MLX -> 
            os == OperatingSystem.MACOS || os == OperatingSystem.IOS
            
        GPUFramework.VULKAN -> 
            os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.ANDROID) ||
            (os == OperatingSystem.MACOS && features.contains(PlatformFeature.GPU_COMPUTING))
            
        GPUFramework.WEBGPU ->
            os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS, OperatingSystem.MACOS)
            
        GPUFramework.DIRECTX12 ->
            os == OperatingSystem.WINDOWS
            
        GPUFramework.CUDA ->
            features.contains(PlatformFeature.GPU_COMPUTING) && 
            os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS)
            
        GPUFramework.ROCM ->
            os == OperatingSystem.LINUX &&
            features.contains(PlatformFeature.GPU_COMPUTING)
            
        GPUFramework.ONEAPI ->
            os in setOf(OperatingSystem.LINUX, OperatingSystem.WINDOWS) &&
            features.contains(PlatformFeature.GPU_COMPUTING)
            
        GPUFramework.OPENCL ->
            features.contains(PlatformFeature.GPU_COMPUTING)
            
        GPUFramework.OPENGL ->
            true // Available on most platforms
            
        GPUFramework.NONE ->
            true
    }
}