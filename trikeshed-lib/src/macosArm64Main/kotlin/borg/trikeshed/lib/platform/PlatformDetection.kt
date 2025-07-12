package borg.trikeshed.lib.platform

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo = PlatformInfo(
        os = OperatingSystem.MACOS,
        arch = Architecture.ARM64,
        version = "macOS"
    )
    
    actual fun hasIoUringSupport(): Boolean = false // macOS doesn't support io_uring
    
    actual fun hasFeature(feature: PlatformFeature): Boolean = when (feature) {
        PlatformFeature.IO_URING -> false
        PlatformFeature.ZERO_COPY -> true
        PlatformFeature.MEMORY_MAPPING -> true
        PlatformFeature.DIRECT_IO -> true
    }
} 