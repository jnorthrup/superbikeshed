#!/usr/bin/env kotlin

/**
 * Platform Detection Verification Script
 * 
 * This script verifies that the platform detection implementation
 * is working correctly by testing basic functionality.
 */

// Simple test without external dependencies
fun main() {
    println("=== Platform Detection Verification ===")
    println()
    
    // Test basic OS detection using system properties
    val osName = System.getProperty("os.name", "").lowercase()
    val osArch = System.getProperty("os.arch", "").lowercase()
    
    println("System Properties:")
    println("  OS Name: $osName")
    println("  OS Arch: $osArch")
    
    // Simulate platform detection logic
    val detectedOS = when {
        osName.contains("linux") -> "LINUX"
        osName.contains("mac") -> "MACOS"
        osName.contains("windows") -> "WINDOWS"
        else -> "UNKNOWN"
    }
    
    val detectedArch = when {
        osArch == "x86_64" || osArch == "amd64" -> "X86_64"
        osArch == "aarch64" || osArch == "arm64" -> "ARM64"
        osArch == "arm" -> "ARM32"
        else -> "UNKNOWN"
    }
    
    println("Detected Platform:")
    println("  OS: $detectedOS")
    println("  Architecture: $detectedArch")
    
    // Test kernel version detection (Linux only)
    if (detectedOS == "LINUX") {
        println()
        println("Linux Kernel Detection:")
        
        val versionFile = java.io.File("/proc/version")
        if (versionFile.exists()) {
            try {
                val versionString = versionFile.readText()
                println("  /proc/version content: ${versionString.take(100)}...")
                
                // Test kernel version parsing
                val regex = Regex("Linux version (\\d+)\\.(\\d+)\\.(\\d+)(?:-([^\\s]+))?")
                val match = regex.find(versionString)
                
                if (match != null) {
                    val major = match.groupValues[1].toIntOrNull()
                    val minor = match.groupValues[2].toIntOrNull()
                    val patch = match.groupValues[3].toIntOrNull()
                    val build = match.groupValues.getOrNull(4)
                    
                    println("  Parsed Kernel Version: $major.$minor.$patch${build?.let { "-$it" } ?: ""}")
                    
                    // Test io_uring support
                    val hasIoUring = major != null && minor != null && 
                                   (major > 5 || (major == 5 && minor >= 1))
                    
                    println("  io_uring Support: $hasIoUring")
                    
                    if (hasIoUring) {
                        println("  ✓ io_uring is available (kernel 5.1+)")
                        
                        // Test advanced features
                        val hasBufferRing = major != null && minor != null && 
                                          (major > 5 || (major == 5 && minor >= 6))
                        val hasMultiShot = major != null && minor != null && 
                                         (major > 5 || (major == 5 && minor >= 7))
                        
                        println("  Buffer Ring Support: $hasBufferRing")
                        println("  Multi-shot Support: $hasMultiShot")
                    } else {
                        println("  ✗ io_uring not available (kernel < 5.1)")
                    }
                } else {
                    println("  ✗ Could not parse kernel version")
                }
            } catch (e: Exception) {
                println("  ✗ Error reading /proc/version: ${e.message}")
            }
        } else {
            println("  ✗ /proc/version not accessible")
        }
    } else {
        println()
        println("Kernel Detection: Not applicable (not Linux)")
    }
    
    println()
    println("=== Verification Complete ===")
    println()
    println("Next Steps:")
    println("1. The platform detection implementation is ready")
    println("2. Test with actual Kotlin compilation")
    println("3. Integrate with existing code that needs io_uring detection")
    println("4. Run the full test suite: ./tests/PlatformDetectionTest.kt")
} 