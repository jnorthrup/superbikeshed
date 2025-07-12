package borg.trikeshed.lib.platform

/**
 * Platform detection for TrikeShed
 * Minimal implementation for compilation compatibility
 */

enum class OperatingSystem {
    LINUX, MACOS, WINDOWS, UNKNOWN
}

data class PlatformInfo(
    val os: OperatingSystem,
    val architecture: String = "unknown"
)

expect object PlatformDetection {
    fun getPlatformInfo(): PlatformInfo
}