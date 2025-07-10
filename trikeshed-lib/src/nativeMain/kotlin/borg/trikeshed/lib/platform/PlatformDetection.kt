@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package borg.trikeshed.lib.platform

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.experimental.ExperimentalNativeApi

/**
 * Native implementation of platform detection
 */
actual object PlatformDetection {
    
    actual fun getPlatformInfo(): PlatformInfo {
        val os = when (Platform.osFamily) {
            OsFamily.MACOSX -> OperatingSystem.MACOS
            OsFamily.LINUX -> OperatingSystem.LINUX
            OsFamily.WINDOWS -> OperatingSystem.WINDOWS
            else -> OperatingSystem.UNKNOWN
        }
        
        val arch = when (Platform.cpuArchitecture) {
            CpuArchitecture.X64 -> Architecture.X64
            CpuArchitecture.ARM64 -> Architecture.ARM64
            CpuArchitecture.ARM32 -> Architecture.ARM32
            else -> Architecture.UNKNOWN
        }
        
        val kernelVersion = getKernelVersion()
        val features = detectFeatures()
        
        return PlatformInfo(os, arch, kernelVersion, features)
    }
    
    actual fun hasIoUringSupport(): Boolean {
        return hasFeature(PlatformFeature.IO_URING)
    }
    
    actual fun hasFeature(feature: PlatformFeature): Boolean {
        val features = detectFeatures()
        return feature in features
    }
    
    actual fun getKernelVersion(): KernelVersion? {
        memScoped {
            val uname = alloc<utsname>()
            if (uname(uname.ptr) == 0) {
                val release = uname.release.toKString()
                val parts = release.split(".")
                if (parts.size >= 3) {
                    return KernelVersion(
                        parts[0].toIntOrNull() ?: 0,
                        parts[1].toIntOrNull() ?: 0,
                        parts[2].substringBefore('-').toIntOrNull() ?: 0
                    )
                }
            }
        }
        return null
    }
    
    internal fun detectFeatures(): Set<PlatformFeature> {
        val features = mutableSetOf<PlatformFeature>()
        
        // Check for IO_URING support (Linux 5.1+)
        if (Platform.osFamily == OsFamily.LINUX) {
            getKernelVersion()?.let { kv ->
                if (kv >= KernelVersion.IO_URING_MINIMUM) {
                    features.add(PlatformFeature.IO_URING)
                }
            }
        }
        
        // Platform-specific features
        when (Platform.osFamily) {
            OsFamily.LINUX -> {
                features.add(PlatformFeature.MMAP)
                features.add(PlatformFeature.SHARED_MEMORY)
                features.add(PlatformFeature.ZERO_COPY)
            }
            OsFamily.MACOSX -> {
                features.add(PlatformFeature.MMAP)
                features.add(PlatformFeature.SHARED_MEMORY)
            }
            else -> {}
        }
        
        // CPU-specific features
        when (Platform.cpuArchitecture) {
            CpuArchitecture.X64 -> {
                features.add(PlatformFeature.SIMD)
                features.add(PlatformFeature.AUTOVECTORIZATION)
                features.add(PlatformFeature.POPCNT)
            }
            CpuArchitecture.ARM64 -> {
                features.add(PlatformFeature.SIMD)
                features.add(PlatformFeature.ARM_NEON)
                features.add(PlatformFeature.AUTOVECTORIZATION)
            }
            else -> {}
        }
        
        return features
    }
}

actual class PlatformInfo actual constructor(
    actual val os: OperatingSystem,
    actual val arch: Architecture,
    actual val kernelVersion: KernelVersion?,
    actual val features: Set<PlatformFeature>
)

actual enum class OperatingSystem {
    LINUX,
    WINDOWS,
    MACOS,
    ANDROID,
    IOS,
    FREEBSD,
    OPENBSD,
    NETBSD,
    UNKNOWN
}

actual enum class Architecture {
    X64,
    ARM64,
    ARM32,
    RISCV64,
    WASM,
    UNKNOWN
}

actual class KernelVersion actual constructor(
    actual val major: Int,
    actual val minor: Int,
    actual val patch: Int,
    actual val extra: String
) : Comparable<KernelVersion> {
    
    actual override fun compareTo(other: KernelVersion): Int {
        val majorComp = major.compareTo(other.major)
        if (majorComp != 0) return majorComp
        
        val minorComp = minor.compareTo(other.minor)
        if (minorComp != 0) return minorComp
        
        return patch.compareTo(other.patch)
    }
    
    actual companion object {
        actual val IO_URING_MINIMUM = KernelVersion(5, 1, 0)
    }
}

actual enum class PlatformFeature {
    IO_URING,
    BUFFER_RING,
    MULTI_SHOT,
    SQPOLL,
    CQPOLL,
    DEFER_TASKRUN,
    SINGLE_ISSUER,
    ATTACH_WQ,
    KERNEL_TLS,
    FIXED_FILES,
    LINKED_OPS,
    MMAP,
    SHARED_MEMORY,
    SIMD,
    NATIVE_CRYPTO,
    JIT,
    AOT,
    VECTOR_INTRINSICS,
    GPU_COMPUTING,
    TPU_COMPUTING,
    NPU_COMPUTING,
    FPGA_COMPUTING,
    CXL_MEMORY,
    PERSISTENT_MEMORY,
    RDMA,
    SPDK,
    DPDK,
    XDP,
    BPF,
    K_TLS,
    QUIC_TLS13,
    HTTP3,
    WEBSOCKETS,
    GRPC,
    KAFKA,
    COUCHDB,
    IPFS,
    DHT,
    LSM_TREE,
    ISAM,
    COLUMNAR_STORAGE,
    ZERO_COPY,
    REGISTER_AT_A_TIME,
    AUTOVECTORIZATION,
    CONCURRENT_SIMD,
    REGISTER_PACKING,
    BRANCHLESS_OPS,
    SWAR,
    TABLE_DRIVEN_DISPATCH,
    PARALLEL_STATE_MACHINE,
    REDUCTION_OPS,
    GATHER_SCATTER,
    NATURAL_STRING_VALIDATION,
    ARM_NEON,
    ARM_SVE,
    POPCNT,
    X86_AVX2,
    X86_AVX512,
    BMI,
    GATHER,
    MASK_OPS,
    VARIABLE_LENGTH
}