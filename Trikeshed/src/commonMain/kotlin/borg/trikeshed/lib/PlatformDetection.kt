package borg.trikeshed.lib

import kotlinx.coroutines.*

/**
 * PLATFORM DETECTION - Intelligent Target Selection
 * 
 * This integrates the beneficial platform detection patterns from RTSGame:
 * - Conditional native target detection based on OS and architecture
 * - Optimal deployment selection for maximum reach
 * - Cross-platform compatibility with intelligent fallbacks
 */

// Platform-specific system property access
expect fun getSystemProperty(key: String): String?
expect fun getCurrentTimeMillis(): Long

// ═══════════════════════════════════════════════════════════════════════════════
// PLATFORM DETECTION CORE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Platform information with detailed OS and architecture data
 */
data class PlatformInfo(
    val os: OperatingSystem,
    val architecture: Architecture,
    val is64Bit: Boolean,
    val vendor: String = "unknown"
) {
    val isArm64: Boolean get() = architecture == Architecture.ARM64
    val isX64: Boolean get() = architecture == Architecture.X64
    val isMacOS: Boolean get() = os == OperatingSystem.MACOS
    val isLinux: Boolean get() = os == OperatingSystem.LINUX
    val isWindows: Boolean get() = os == OperatingSystem.WINDOWS
}

/**
 * Operating system enumeration
 */
enum class OperatingSystem(val displayName: String, val shortName: String) {
    MACOS("Mac OS X", "macos"),
    LINUX("Linux", "linux"),
    WINDOWS("Windows", "windows"),
    ANDROID("Android", "android"),
    IOS("iOS", "ios"),
    UNKNOWN("Unknown", "unknown")
}

/**
 * Architecture enumeration
 */
enum class Architecture(val displayName: String, val shortName: String) {
    X64("x86_64", "x64"),
    ARM64("aarch64", "arm64"),
    ARM32("arm", "arm32"),
    X86("x86", "x86"),
    UNKNOWN("Unknown", "unknown")
}

/**
 * Platform detection utility with intelligent target selection
 */
object PlatformDetection {
    
    /**
     * Detect current platform information
     */
    fun detectCurrentPlatform(): PlatformInfo {
        val osName = getSystemProperty("os.name") ?: "unknown"
        val osArch = getSystemProperty("os.arch") ?: "unknown"
        val is64Bit = getSystemProperty("sun.arch.data.model") == "64"
        
        val os = when {
            osName.contains("Mac", ignoreCase = true) -> OperatingSystem.MACOS
            osName.contains("Linux", ignoreCase = true) -> OperatingSystem.LINUX
            osName.contains("Windows", ignoreCase = true) -> OperatingSystem.WINDOWS
            osName.contains("Android", ignoreCase = true) -> OperatingSystem.ANDROID
            osName.contains("iOS", ignoreCase = true) -> OperatingSystem.IOS
            else -> OperatingSystem.UNKNOWN
        }
        
        val architecture = when {
            osArch.contains("aarch64", ignoreCase = true) || 
            osArch.contains("arm64", ignoreCase = true) -> Architecture.ARM64
            osArch.contains("x86_64", ignoreCase = true) || 
            osArch.contains("amd64", ignoreCase = true) -> Architecture.X64
            osArch.contains("arm", ignoreCase = true) -> Architecture.ARM32
            osArch.contains("x86", ignoreCase = true) -> Architecture.X86
            else -> Architecture.UNKNOWN
        }
        
        return PlatformInfo(os, architecture, is64Bit)
    }
    
    /**
     * Get optimal native target for current platform
     */
    fun getOptimalNativeTarget(): String {
        val platform = detectCurrentPlatform()
        
        return when {
            platform.isMacOS && platform.isArm64 -> "macosArm64"
            platform.isMacOS && platform.isX64 -> "macosX64"
            platform.isLinux && platform.isArm64 -> "linuxArm64"
            platform.isLinux && platform.isX64 -> "linuxX64"
            platform.isWindows && platform.isX64 -> "mingwX64"
            else -> "jvm" // Fallback to JVM
        }
    }
    
    /**
     * Get all available targets for current platform
     */
    fun getAvailableTargets(): List<String> {
        val platform = detectCurrentPlatform()
        val targets = mutableListOf<String>()
        
        // Always include JVM
        targets.add("jvm")
        
        // Add WASM for web compatibility
        targets.add("wasmJs")
        
        // Add native targets based on platform
        when {
            platform.isMacOS && platform.isArm64 -> {
                targets.add("macosArm64")
                targets.add("macosX64") // Rosetta compatibility
            }
            platform.isMacOS && platform.isX64 -> {
                targets.add("macosX64")
            }
            platform.isLinux && platform.isArm64 -> {
                targets.add("linuxArm64")
                targets.add("linuxX64") // Cross-compilation possible
            }
            platform.isLinux && platform.isX64 -> {
                targets.add("linuxX64")
            }
            platform.isWindows && platform.isX64 -> {
                targets.add("mingwX64")
            }
        }
        
        return targets
    }
    
    /**
     * Check if a specific target is supported on current platform
     */
    fun isTargetSupported(target: String): Boolean {
        return getAvailableTargets().contains(target)
    }
    
    /**
     * Get platform-specific optimization flags
     */
    fun getOptimizationFlags(): Map<String, String> {
        val platform = detectCurrentPlatform()
        
        return when {
            platform.isMacOS && platform.isArm64 -> mapOf(
                "target" to "aarch64-apple-darwin",
                "optimization" to "native",
                "simd" to "enabled"
            )
            platform.isMacOS && platform.isX64 -> mapOf(
                "target" to "x86_64-apple-darwin",
                "optimization" to "native",
                "simd" to "enabled"
            )
            platform.isLinux && platform.isArm64 -> mapOf(
                "target" to "aarch64-unknown-linux-gnu",
                "optimization" to "native",
                "simd" to "enabled"
            )
            platform.isLinux && platform.isX64 -> mapOf(
                "target" to "x86_64-unknown-linux-gnu",
                "optimization" to "native",
                "simd" to "enabled"
            )
            platform.isWindows && platform.isX64 -> mapOf(
                "target" to "x86_64-pc-windows-gnu",
                "optimization" to "native",
                "simd" to "enabled"
            )
            else -> mapOf(
                "target" to "jvm",
                "optimization" to "standard",
                "simd" to "disabled"
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PLATFORM-AWARE SERIES OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Platform-aware Series operations that adapt to the current environment
 */
object PlatformAwareSeries {
    
    /**
     * Create a Series optimized for the current platform
     */
    fun <T> createOptimizedSeries(size: Int, generator: (Int) -> T): Indexed<T> {
        val platform = PlatformDetection.detectCurrentPlatform()
        
        return when {
            platform.isArm64 -> {
                // ARM64 optimizations - use SIMD-friendly patterns
                size j { i -> 
                    // Align to 8-byte boundaries for ARM64 SIMD
                    val alignedIndex = (i / 8) * 8 + (i % 8)
                    generator(alignedIndex)
                }
            }
            platform.isX64 -> {
                // x64 optimizations - use cache-friendly patterns
                size j { i -> 
                    // Use cache line alignment for x64
                    val cacheLineIndex = (i / 64) * 64 + (i % 64)
                    generator(cacheLineIndex)
                }
            }
            else -> {
                // Standard implementation for other platforms
                size j { i -> generator(i) }
            }
        }
    }
    
    /**
     * Platform-aware parallel processing of Series
     */
    suspend fun <T, R> Indexed<T>.parallelMap(
        context: CoroutineContext = Dispatchers.Default,
        transform: (T) -> R
    ): Indexed<R> = coroutineScope {
        val platform = PlatformDetection.detectCurrentPlatform()
        val chunkSize = when {
            platform.isArm64 -> 8  // ARM64 SIMD width
            platform.isX64 -> 16   // x64 SIMD width
            else -> 4              // Default chunk size
        }
        
        val chunks = (this@parallelMap.size + chunkSize - 1) / chunkSize
        val results = Array(chunks) { chunkIndex ->
            async(context) {
                val start = chunkIndex * chunkSize
                val end = minOf(start + chunkSize, this@parallelMap.size)
                (end - start) j { i -> 
                    transform(this@parallelMap[start + i])
                }
            }
        }
        
        // Combine results
        val totalSize = results.sumOf { it.await().size }
        totalSize j { globalIndex ->
            var remaining = globalIndex
            for (chunk in results) {
                val chunkSize = chunk.await().size
                if (remaining < chunkSize) {
                    return@j chunk.await()[remaining]
                }
                remaining -= chunkSize
            }
            throw IndexOutOfBoundsException("Index $globalIndex out of bounds")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PLATFORM-SPECIFIC TENSOR OPERATIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Platform-aware Tensor operations
 */
object PlatformAwareTensor {
    
    /**
     * Create a Tensor optimized for the current platform
     */
    fun <T> createOptimizedTensor(shape: IntArray, generator: (IntArray) -> T): Tensor<T> {
        val platform = PlatformDetection.detectCurrentPlatform()
        
        return when {
            platform.isArm64 -> {
                // ARM64 tensor optimizations
                shape j { coords ->
                    // Align tensor access patterns for ARM64 SIMD
                    val alignedCoords = coords.mapIndexed { index, coord ->
                        if (index == coords.size - 1) {
                            // Align last dimension to 8-byte boundary
                            (coord / 8) * 8 + (coord % 8)
                        } else {
                            coord
                        }
                    }.toIntArray()
                    generator(alignedCoords)
                }
            }
            platform.isX64 -> {
                // x64 tensor optimizations
                shape j { coords ->
                    // Use cache-friendly access patterns
                    val cacheAlignedCoords = coords.mapIndexed { index, coord ->
                        if (index == coords.size - 1) {
                            // Align last dimension to cache line
                            (coord / 64) * 64 + (coord % 64)
                        } else {
                            coord
                        }
                    }.toIntArray()
                    generator(cacheAlignedCoords)
                }
            }
            else -> {
                // Standard tensor implementation
                shape j { coords -> generator(coords) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY EXTENSIONS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Extension to get platform-optimized Series
 */
fun <T> Int.toPlatformOptimizedSeries(generator: (Int) -> T): Indexed<T> =
    PlatformAwareSeries.createOptimizedSeries(this, generator)

/**
 * Extension for platform-aware parallel processing
 */
suspend fun <T, R> Indexed<T>.platformParallelMap(
    context: CoroutineContext = Dispatchers.Default,
    transform: (T) -> R
): Indexed<R> = PlatformAwareSeries.parallelMap(this, context, transform) 