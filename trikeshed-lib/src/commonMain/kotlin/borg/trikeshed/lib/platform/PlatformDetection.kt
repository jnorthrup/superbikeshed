package borg.trikeshed.lib.platform

expect object PlatformDetection {
    fun getPlatformInfo(): PlatformInfo
}

data class PlatformInfo(
    val os: OperatingSystem,
    val arch: Architecture,
    val is64Bit: Boolean
)

enum class OperatingSystem {
    LINUX,
    MACOS,
    WINDOWS,
    ANDROID,
    IOS,
    UNKNOWN
}

enum class Architecture {
    X86_64,
    ARM64,
    ARM32,
    X86_32,
    UNKNOWN
} 