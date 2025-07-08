package tests

import borg.trikeshed.lib.platform.*
import kotlin.test.*

/**
 * Tests for platform detection functionality
 */
class PlatformDetectionTest {
    
    @Test
    fun testPlatformDetection() {
        val info = PlatformDetection.getPlatformInfo()
        
        // Basic platform info should be available
        assertNotNull(info.os)
        assertNotNull(info.arch)
        assertNotNull(info.features)
        
        println("Platform: ${info.os}")
        println("Architecture: ${info.arch}")
        println("Features: ${info.features}")
        
        if (info.kernelVersion != null) {
            println("Kernel Version: ${info.kernelVersion}")
        }
    }
    
    @Test
    fun testIoUringDetection() {
        val hasUring = PlatformDetection.hasIoUringSupport()
        val info = PlatformDetection.getPlatformInfo()
        
        if (info.os == OperatingSystem.LINUX) {
            if (info.kernelVersion != null) {
                val expected = info.kernelVersion >= KernelVersion.IO_URING_MINIMUM
                assertEquals(expected, hasUring, "io_uring support should match kernel version")
                
                if (hasUring) {
                    assertTrue(info.features.contains(PlatformFeature.IO_URING))
                    println("io_uring is supported on kernel ${info.kernelVersion}")
                } else {
                    println("io_uring is not supported on kernel ${info.kernelVersion}")
                }
            } else {
                // Can't determine without kernel version
                println("Kernel version not available, io_uring support: $hasUring")
            }
        } else {
            assertFalse(hasUring, "io_uring should not be supported on non-Linux platforms")
            println("io_uring not supported on ${info.os}")
        }
    }
    
    @Test
    fun testFeatureDetection() {
        val info = PlatformDetection.getPlatformInfo()
        
        // Test specific features
        val hasZeroCopy = PlatformDetection.hasFeature(PlatformFeature.ZERO_COPY)
        val hasIoUring = PlatformDetection.hasFeature(PlatformFeature.IO_URING)
        
        println("Zero-copy support: $hasZeroCopy")
        println("io_uring support: $hasIoUring")
        
        // Zero-copy should be available on most platforms
        assertTrue(hasZeroCopy, "Zero-copy should be available")
        
        // io_uring should only be available on Linux with kernel 5.1+
        if (info.os == OperatingSystem.LINUX && info.kernelVersion != null) {
            val expected = info.kernelVersion >= KernelVersion.IO_URING_MINIMUM
            assertEquals(expected, hasIoUring, "io_uring feature should match kernel version")
        } else {
            assertFalse(hasIoUring, "io_uring should not be available on non-Linux platforms")
        }
    }
    
    @Test
    fun testKernelVersionParsing() {
        // Test kernel version parsing
        val testVersions = listOf(
            "Linux version 5.15.0-91-generic" to KernelVersion(5, 15, 0, "91-generic"),
            "Linux version 5.1.0" to KernelVersion(5, 1, 0),
            "Linux version 4.19.0-1-amd64" to KernelVersion(4, 19, 0, "1-amd64"),
            "Linux version 6.0.0" to KernelVersion(6, 0, 0)
        )
        
        for ((versionString, expected) in testVersions) {
            val parsed = parseKernelVersion(versionString)
            assertNotNull(parsed, "Should parse kernel version: $versionString")
            assertEquals(expected, parsed, "Parsed version should match expected for: $versionString")
        }
    }
    
    @Test
    fun testKernelVersionComparison() {
        val v5_0 = KernelVersion(5, 0, 0)
        val v5_1 = KernelVersion(5, 1, 0)
        val v5_2 = KernelVersion(5, 2, 0)
        val v6_0 = KernelVersion(6, 0, 0)
        
        // Test comparisons
        assertTrue(v5_1 >= KernelVersion.IO_URING_MINIMUM)
        assertTrue(v5_2 >= KernelVersion.IO_URING_MINIMUM)
        assertTrue(v6_0 >= KernelVersion.IO_URING_MINIMUM)
        assertFalse(v5_0 >= KernelVersion.IO_URING_MINIMUM)
        
        // Test ordering
        assertTrue(v5_0 < v5_1)
        assertTrue(v5_1 < v5_2)
        assertTrue(v5_2 < v6_0)
    }
    
    @Test
    fun testConvenienceFunctions() {
        // Test convenience functions
        val isLinux = isLinux()
        val isMacOS = isMacOS()
        val hasUring = isLinuxWithUring()
        
        println("isLinux(): $isLinux")
        println("isMacOS(): $isMacOS")
        println("isLinuxWithUring(): $hasUring")
        
        // These should be consistent with platform detection
        val info = PlatformDetection.getPlatformInfo()
        assertEquals(info.os == OperatingSystem.LINUX, isLinux)
        assertEquals(info.os == OperatingSystem.MACOS, isMacOS)
        assertEquals(PlatformDetection.hasIoUringSupport(), hasUring)
    }
    
    /**
     * Helper function to test kernel version parsing
     */
    internal fun parseKernelVersion(versionString: String): KernelVersion? {
        val regex = Regex("Linux version (\\d+)\\.(\\d+)\\.(\\d+)(?:-([^\\s]+))?")
        val match = regex.find(versionString) ?: return null
        
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        val build = match.groupValues.getOrNull(4)
        
        return KernelVersion(major, minor, patch, build)
    }
} 