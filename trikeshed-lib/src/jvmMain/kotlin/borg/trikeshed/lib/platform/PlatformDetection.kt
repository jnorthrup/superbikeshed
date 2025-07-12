package borg.trikeshed.lib.platform

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo {
        val osName = System.getProperty("os.name", "unknown").lowercase()
        val osArch = System.getProperty("os.arch", "unknown").lowercase()
        
        val os = when {
            osName.contains("linux") -> OperatingSystem.LINUX
            osName.contains("mac") -> OperatingSystem.MACOS
            osName.contains("windows") -> OperatingSystem.WINDOWS
            osName.contains("android") -> OperatingSystem.ANDROID
            else -> OperatingSystem.UNKNOWN
        }
        
        val arch = when {
            osArch.contains("x86_64") || osArch.contains("amd64") -> Architecture.X86_64
            osArch.contains("aarch64") || osArch.contains("arm64") -> Architecture.ARM64
            osArch.contains("arm") -> Architecture.ARM32
            osArch.contains("x86") -> Architecture.X86_32
            else -> Architecture.UNKNOWN
        }
        
        return PlatformInfo(os, arch, osName)
    }
    
    actual fun hasIoUringSupport(): Boolean {
        // JVM doesn't have direct access to io_uring
        return false
    }
    
    actual fun hasFeature(feature: PlatformFeature): Boolean = when (feature) {
        PlatformFeature.IO_URING -> false
        PlatformFeature.ZERO_COPY -> true
        PlatformFeature.MEMORY_MAPPING -> true
        PlatformFeature.DIRECT_IO -> true
    }
} 