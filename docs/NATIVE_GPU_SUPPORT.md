# Native GPU Support for MLX, Vulkan, and WebGPU

This document describes the native build configuration for GPU acceleration frameworks across different platforms.

## Overview

The v2superbikeshed project supports native builds with GPU acceleration on non-Linux platforms through:

- **macOS**: Metal, MLX (Apple's ML framework)
- **Windows**: Vulkan, WebGPU, DirectX 12
- **Linux**: Vulkan, WebGPU, ROCm, CUDA
- **Cross-platform**: OpenCL, OpenGL

## Build Configuration

### Prerequisites

#### macOS (Apple Silicon & Intel)
- Xcode with Metal development tools
- MLX (optional): `brew install mlx` or build from source
- MoltenVK (optional): For Vulkan support on macOS

#### Windows
- Vulkan SDK: Download from [LunarG](https://vulkan.lunarg.com/)
- WebGPU: Dawn or wgpu-native
- Visual Studio with C++ development tools

#### Linux
- Vulkan: `sudo apt install libvulkan-dev` (Ubuntu/Debian)
- WebGPU: Build Dawn or wgpu-native from source
- CUDA: Install NVIDIA CUDA Toolkit
- ROCm: Install AMD ROCm stack

### Environment Variables

Set these environment variables for non-standard installations:

```bash
# Windows
export VULKAN_SDK="C:/VulkanSDK/1.3.268.0"
export WEBGPU_SDK="C:/webgpu"

# Linux/macOS with custom paths
export VULKAN_SDK="/opt/vulkan"
export WEBGPU_SDK="/opt/webgpu"
```

### Building with GPU Support

The build system automatically detects available GPU frameworks:

```bash
# Build for current platform with GPU support
./gradlew :platform-launcher:build

# Build specific native target
./gradlew :platform-launcher:macosArm64MainBinaries  # Apple Silicon
./gradlew :platform-launcher:mingwX64MainBinaries    # Windows
./gradlew :platform-launcher:linuxX64MainBinaries    # Linux x64
```

## GPU Framework Integration

### Metal (macOS)

Metal support is automatically available on macOS. The build includes:
- Metal compute shaders
- MetalPerformanceShaders
- Metal ray tracing (M3 and newer)

### MLX (macOS Apple Silicon)

MLX provides optimized machine learning operations:
- Unified memory model
- Lazy evaluation
- Metal backend acceleration

### Vulkan (Windows/Linux)

Vulkan provides low-level GPU control:
- Compute shaders
- Graphics pipelines
- Multi-GPU support

### WebGPU (Cross-platform)

WebGPU offers a modern, simplified GPU API:
- Compute and graphics
- Safety by design
- Cross-platform compatibility

## Platform Detection

The `GPUDetection` API provides runtime detection:

```kotlin
import borg.trikeshed.lib.platform.GPUDetection

// Check available frameworks
val frameworks = GPUDetection.getAvailableFrameworks()
if (GPUFramework.MLX in frameworks) {
    // Use MLX for ML operations
}

// Get GPU capabilities
val capabilities = GPUDetection.getGPUCapabilities()
println("Unified memory: ${capabilities.unifiedMemory}")
println("Max memory: ${capabilities.maxMemoryGB} GB")
```

## Module-Specific GPU Usage

### trikeshed-json
- M3NativeJsonScanner: Uses Metal/Accelerate for JSON parsing
- Hardware-accelerated structural indexing

### fiduciary
- Attention mechanisms can use GPU for parallel processing
- Document analysis acceleration

### rtsgame
- WebGPU renderer for cross-platform graphics
- Compute shaders for physics simulation

## Troubleshooting

### macOS
- Ensure Xcode command line tools are installed: `xcode-select --install`
- For MLX: Check installation with `ls -la /opt/homebrew/lib/libmlx.dylib`

### Windows
- Set VULKAN_SDK environment variable
- Install Visual C++ redistributables

### Linux
- Install GPU drivers (NVIDIA, AMD, Intel)
- Check Vulkan with `vulkaninfo`
- For CUDA: Verify with `nvidia-smi`

## Performance Considerations

1. **Memory Transfer**: Minimize CPU-GPU memory transfers
2. **Batch Operations**: Process data in batches for GPU efficiency
3. **Framework Selection**: Choose the right framework for your use case:
   - MLX: Best for ML on Apple Silicon
   - Vulkan: Maximum control and performance
   - WebGPU: Portability and ease of use

## Future Support

Planned additions:
- Intel oneAPI for Intel GPUs
- AMD HIP for cross-vendor compute
- OpenXLA for ML model compilation
- SPIR-V shader cross-compilation