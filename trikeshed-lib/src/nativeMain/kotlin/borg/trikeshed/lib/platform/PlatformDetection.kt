package borg.trikeshed.lib.platform

import platform.posix.uname
import platform.posix.utsname

actual object PlatformDetection {
    actual fun getPlatformInfo(): PlatformInfo {
        val utsname = uname(null)
        val sysname = utsname?.sysname?.toKString()?.lowercase() ?: "unknown"
        val machine = utsname?.machine?.toKString()?.lowercase() ?: "unknown"
        
        val os = when {
            sysname.contains("linux") -> OperatingSystem.LINUX
            sysname.contains("darwin") -> OperatingSystem.MACOS
            sysname.contains("windows") -> OperatingSystem.WINDOWS
            sysname.contains("android") -> OperatingSystem.ANDROID
            else -> OperatingSystem.UNKNOWN
        }
        
        val arch = when {
            machine.contains("x86_64") || machine.contains("amd64") -> Architecture.X86_64
            machine.contains("aarch64") || machine.contains("arm64") -> Architecture.ARM64
            machine.contains("arm") -> Architecture.ARM32
            machine.contains("i386") || machine.contains("i686") -> Architecture.X86_32
            else -> Architecture.UNKNOWN
        }
        
        val is64Bit = machine.contains("64") || machine.contains("aarch64")
        
        return PlatformInfo(os, arch, is64Bit)
    }
} 