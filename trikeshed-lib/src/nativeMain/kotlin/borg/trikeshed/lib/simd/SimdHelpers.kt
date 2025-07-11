@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package borg.trikeshed.lib.simd

import platform.posix.*
import kotlinx.cinterop.*
import kotlin.experimental.ExperimentalNativeApi
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.j
// Remove all toIndexed and toIntArray extension function definitions from this file.

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

/**
 * Helper functions for SIMD detection and utilities
 */

internal fun hasArmSve(): Boolean {
    // Check for ARM SVE support
    memScoped {
        val buffer = allocArray<ByteVar>(256)
        val file = fopen("/proc/cpuinfo", "r")
        if (file != null) {
            while (fgets(buffer, 256, file) != null) {
                val line = buffer.toKString()
                if (line.contains("Features") && line.contains("sve")) {
                    fclose(file)
                    return true
                }
            }
            fclose(file)
        }
    }
    return false
}

internal fun getArmCpuFeatures(): Set<String> {
    val features = mutableSetOf<String>()
    
    memScoped {
        val buffer = allocArray<ByteVar>(256)
        val file = fopen("/proc/cpuinfo", "r")
        if (file != null) {
            while (fgets(buffer, 256, file) != null) {
                val line = buffer.toKString()
                if (line.startsWith("Features")) {
                    val featureList = line.substringAfter(":").trim().split(" ")
                    features.addAll(featureList)
                    break
                }
            }
            fclose(file)
        }
    }
    
    return features
}

/**
 * Platform detection helpers
 */
internal fun isMacOSArmNative(): Boolean {
    return Platform.osFamily == OsFamily.MACOSX && 
           Platform.cpuArchitecture == CpuArchitecture.ARM64
}

internal fun isLinuxArm64(): Boolean {
    return Platform.osFamily == OsFamily.LINUX && 
           Platform.cpuArchitecture == CpuArchitecture.ARM64
}

/**
 * Performance measurement helpers
 */
fun measureNanoTime(block: () -> Unit): Long {
    memScoped {
        val start = alloc<timespec>()
        val end = alloc<timespec>()
        
        clock_gettime(CLOCK_MONOTONIC.toUInt(), start.ptr)
        block()
        clock_gettime(CLOCK_MONOTONIC.toUInt(), end.ptr)
        
        val startNs = start.tv_sec * 1_000_000_000L + start.tv_nsec
        val endNs = end.tv_sec * 1_000_000_000L + end.tv_nsec
        
        return endNs - startNs
    }
}