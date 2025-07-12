package borg.trikeshed.lib.platform

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch")
        
        val os = when {
            osName.contains("linux") -> OperatingSystem.LINUX
            osName.contains("mac") || osName.contains("darwin") -> OperatingSystem.MACOS
            osName.contains("windows") -> OperatingSystem.WINDOWS
            else -> OperatingSystem.UNKNOWN
        }
        
        return PlatformInfo(os, arch)
    }
}