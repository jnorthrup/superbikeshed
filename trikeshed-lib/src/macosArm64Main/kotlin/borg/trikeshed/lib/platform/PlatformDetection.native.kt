package borg.trikeshed.lib.platform

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo {
        return PlatformInfo(OperatingSystem.MACOS, "arm64")
    }
}