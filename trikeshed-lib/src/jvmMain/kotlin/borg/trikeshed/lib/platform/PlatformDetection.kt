package borg.trikeshed.lib.platform

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()
        
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
        
        val is64Bit = osArch.contains("64") || osArch.contains("aarch64")
        
        return PlatformInfo(os, arch, is64Bit)
    }
} 