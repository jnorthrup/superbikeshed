package borg.trikeshed.lib.platform

/**
 * Platform information
 */
data class PlatformInfo(
    val os: OperatingSystem,
    val arch: Architecture,
    val version: String = "unknown"
)

/**
 * Operating system enumeration
 */
enum class OperatingSystem {
    LINUX, MACOS, WINDOWS, ANDROID, IOS, WASM, UNKNOWN
}

/**
 * Architecture enumeration
 */
enum class Architecture {
    X86_64, ARM64, ARM32, X86_32, WASM32, UNKNOWN
}

/**
 * Platform features
 */
enum class PlatformFeature {
    IO_URING, ZERO_COPY, MEMORY_MAPPING, DIRECT_IO
}

/**
 * Platform detection interface
 */
expect object PlatformDetection {
    fun getPlatformInfo(): PlatformInfo
    fun hasIoUringSupport(): Boolean
    fun hasFeature(feature: PlatformFeature): Boolean
} 