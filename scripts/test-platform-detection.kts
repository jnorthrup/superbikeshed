#!/usr/bin/env kotlin

@file:DependsOn("trikeshed-lib")

import borg.trikeshed.lib.platform.*

/**
 * Platform Detection Demo
 * 
 * This script demonstrates the platform detection functionality
 * and shows how to properly detect Linux kernel version with io_uring support.
 */

fun main() {
    println("=== Trikeshed Platform Detection Demo ===")
    println()
    
    // Get platform information
    val info = PlatformDetection.getPlatformInfo()
    
    println("Operating System: ${info.os}")
    println("Architecture: ${info.arch}")
    
    if (info.kernelVersion != null) {
        println("Kernel Version: ${info.kernelVersion}")
        println("Kernel supports io_uring: ${info.kernelVersion >= KernelVersion.IO_URING_MINIMUM}")
    } else {
        println("Kernel Version: Not available (not Linux or /proc/version not accessible)")
    }
    
    println()
    println("Available Features:")
    info.features.forEach { feature ->
        println("  - $feature")
    }
    
    println()
    println("io_uring Support:")
    val hasUring = PlatformDetection.hasIoUringSupport()
    println("  Supported: $hasUring")
    
    if (hasUring) {
        println("  ✓ io_uring is available for high-performance I/O")
        println("  ✓ Can use zero-copy operations")
        println("  ✓ Can use multi-shot operations")
        println("  ✓ Can use buffer rings")
        
        // Check for advanced features
        if (PlatformDetection.hasFeature(PlatformFeature.BUFFER_RING)) {
            println("  ✓ Buffer ring support available")
        }
        if (PlatformDetection.hasFeature(PlatformFeature.MULTI_SHOT)) {
            println("  ✓ Multi-shot operations available")
        }
        if (PlatformDetection.hasFeature(PlatformFeature.SQPOLL)) {
            println("  ✓ Submission queue polling available")
        }
    } else {
        println("  ✗ io_uring not available")
        if (info.os != OperatingSystem.LINUX) {
            println("    Reason: Not running on Linux")
        } else if (info.kernelVersion != null) {
            println("    Reason: Kernel version ${info.kernelVersion} < ${KernelVersion.IO_URING_MINIMUM}")
        } else {
            println("    Reason: Cannot determine kernel version")
        }
    }
    
    println()
    println("Convenience Functions:")
    println("  isLinux(): ${isLinux()}")
    println("  isMacOS(): ${isMacOS()}")
    println("  isLinuxWithUring(): ${isLinuxWithUring()}")
    println("  hasFeature(ZERO_COPY): ${hasFeature(PlatformFeature.ZERO_COPY)}")
    
    println()
    println("=== Demo Complete ===")
} 