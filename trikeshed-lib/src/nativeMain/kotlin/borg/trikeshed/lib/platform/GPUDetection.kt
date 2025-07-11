package borg.trikeshed.lib.platform

import kotlinx.cinterop.*
import platform.posix.*

/**
 * Native implementation of GPU framework detection
 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
actual object GPUDetection {
    actual fun getAvailableFrameworks(): Set<GPUFramework> {
        val frameworks = mutableSetOf<GPUFramework>()
        
        // Platform-specific detection
        when (Platform.osFamily) {
            OsFamily.MACOSX -> {
                frameworks.add(GPUFramework.METAL)
                frameworks.add(GPUFramework.MLX)
                frameworks.add(GPUFramework.OPENGL)
            }
            OsFamily.IOS -> {
                frameworks.add(GPUFramework.METAL)
                frameworks.add(GPUFramework.MLX)
                frameworks.add(GPUFramework.OPENGL)
            }
            OsFamily.LINUX -> {
                frameworks.add(GPUFramework.OPENGL)
                frameworks.add(GPUFramework.VULKAN)
                frameworks.add(GPUFramework.OPENCL)
                // Check for CUDA/ROCM
                if (isCUDAAvailable()) frameworks.add(GPUFramework.CUDA)
                if (isROCMAvailable()) frameworks.add(GPUFramework.ROCM)
                if (isOneAPIAvailable()) frameworks.add(GPUFramework.ONEAPI)
            }
            OsFamily.WINDOWS -> {
                frameworks.add(GPUFramework.OPENGL)
                frameworks.add(GPUFramework.VULKAN)
                frameworks.add(GPUFramework.DIRECTX12)
                frameworks.add(GPUFramework.OPENCL)
                if (isCUDAAvailable()) frameworks.add(GPUFramework.CUDA)
                if (isOneAPIAvailable()) frameworks.add(GPUFramework.ONEAPI)
            }
            OsFamily.UNKNOWN, OsFamily.ANDROID, OsFamily.WASM, OsFamily.TVOS, OsFamily.WATCHOS -> {
                frameworks.add(GPUFramework.OPENGL)
            }
        }
        
        return frameworks
    }
    
    actual fun hasFramework(framework: GPUFramework): Boolean {
        return when (framework) {
            GPUFramework.METAL, GPUFramework.MLX -> 
                Platform.osFamily in setOf(OsFamily.MACOSX, OsFamily.IOS)
            GPUFramework.DIRECTX12 -> 
                Platform.osFamily == OsFamily.WINDOWS
            GPUFramework.VULKAN -> 
                Platform.osFamily in setOf(OsFamily.LINUX, OsFamily.WINDOWS, OsFamily.ANDROID)
            GPUFramework.CUDA -> 
                isCUDAAvailable()
            GPUFramework.ROCM -> 
                Platform.osFamily == OsFamily.LINUX && isROCMAvailable()
            GPUFramework.ONEAPI -> 
                isOneAPIAvailable()
            GPUFramework.OPENCL -> 
                true // Available on most platforms
            GPUFramework.OPENGL -> 
                true // Available on most platforms
            GPUFramework.WEBGPU -> 
                Platform.osFamily in setOf(OsFamily.LINUX, OsFamily.WINDOWS, OsFamily.MACOSX)
            GPUFramework.NONE -> 
                true
        }
    }
    
    actual fun getGPUCapabilities(): GPUCapabilities {
        val frameworks = getAvailableFrameworks()
        val devices = mutableListOf<GPUDevice>()
        
        // Platform-specific device detection
        when (Platform.osFamily) {
            OsFamily.MACOSX -> {
                // Apple Silicon detection
                if (Platform.cpuArchitecture == CpuArchitecture.ARM64) {
                    devices.add(GPUDevice(
                        name = "Apple Silicon GPU",
                        vendor = GPUVendor.APPLE,
                        memoryGB = 8.0f, // Unified memory
                        computeUnits = 10, // M1/M2/M3 typical
                        framework = GPUFramework.METAL
                    ))
                }
            }
            OsFamily.LINUX -> {
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
            OsFamily.WINDOWS -> {
                // Windows GPU detection
                devices.add(GPUDevice(
                    name = "Windows GPU",
                    vendor = GPUVendor.UNKNOWN,
                    memoryGB = 4.0f,
                    computeUnits = 1024,
                    framework = GPUFramework.DIRECTX12
                ))
            }
            OsFamily.UNKNOWN, OsFamily.IOS, OsFamily.ANDROID, OsFamily.WASM, OsFamily.TVOS, OsFamily.WATCHOS -> {
                // Other platforms - no GPU detection
            }
        }
        
        // Add CPU fallback
        devices.add(GPUDevice(
            name = "Native CPU",
            vendor = GPUVendor.UNKNOWN,
            memoryGB = 16.0f,
            computeUnits = 8, // Default CPU cores
            framework = GPUFramework.NONE
        ))
        
        return GPUCapabilities(
            availableFrameworks = frameworks,
            devices = devices,
            unifiedMemory = Platform.osFamily in setOf(OsFamily.MACOSX, OsFamily.IOS),
            computeCapability = when (Platform.osFamily) {
                OsFamily.MACOSX -> ComputeCapability.METAL_3_0
                else -> ComputeCapability.NONE
            },
            maxMemoryGB = 16.0f,
            tensorCores = Platform.osFamily in setOf(OsFamily.MACOSX, OsFamily.IOS),
            rayTracingCores = false,
            neuralCores = Platform.osFamily in setOf(OsFamily.MACOSX, OsFamily.IOS)
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