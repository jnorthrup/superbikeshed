package borg.trikeshed.lib.platform

import java.io.File

/**
 * JVM implementation of platform detection
 */
actual object PlatformDetection {
    
    actual fun getPlatformInfo(): PlatformInfo {
        val os = detectOperatingSystem()
        val arch = detectArchitecture()
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
        val osName = System.getProperty("os.name").lowercase()
        if (osName.contains("linux")) {
            try {
                val release = Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim()
                val parts = release.split(".")
                if (parts.size >= 3) {
                    return KernelVersion(
                        parts[0].toIntOrNull() ?: 0,
                        parts[1].toIntOrNull() ?: 0,
                        parts[2].substringBefore('-').toIntOrNull() ?: 0
                    )
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }
    
    internal fun detectOperatingSystem(): OperatingSystem {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("linux") -> OperatingSystem.LINUX
            osName.contains("windows") -> OperatingSystem.WINDOWS
            osName.contains("mac") || osName.contains("darwin") -> OperatingSystem.MACOS
            osName.contains("android") -> OperatingSystem.ANDROID
            osName.contains("freebsd") -> OperatingSystem.FREEBSD
            osName.contains("openbsd") -> OperatingSystem.OPENBSD
            osName.contains("netbsd") -> OperatingSystem.NETBSD
            else -> OperatingSystem.UNKNOWN
        }
    }
    
    internal fun detectArchitecture(): Architecture {
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            arch.contains("amd64") || arch.contains("x86_64") -> Architecture.X64
            arch.contains("aarch64") || arch.contains("arm64") -> Architecture.ARM64
            arch.contains("arm") -> Architecture.ARM32
            arch.contains("riscv64") -> Architecture.RISCV64
            else -> Architecture.UNKNOWN
        }
    }
    
    internal fun detectFeatures(): Set<PlatformFeature> {
        val features = mutableSetOf<PlatformFeature>()
        
        // JVM always has these
        features.add(PlatformFeature.MMAP)
        features.add(PlatformFeature.SHARED_MEMORY)
        features.add(PlatformFeature.JIT)
        features.add(PlatformFeature.NATIVE_CRYPTO)
        
        // Check for IO_URING support (Linux 5.1+)
        if (detectOperatingSystem() == OperatingSystem.LINUX) {
            getKernelVersion()?.let { kv ->
                if (kv >= KernelVersion.IO_URING_MINIMUM) {
                    features.add(PlatformFeature.IO_URING)
                }
            }
        }
        
        // Architecture-specific features
        when (detectArchitecture()) {
            Architecture.X64 -> {
                features.add(PlatformFeature.SIMD)
                features.add(PlatformFeature.AUTOVECTORIZATION)
                features.add(PlatformFeature.POPCNT)
                features.add(PlatformFeature.X86_AVX2)
            }
            Architecture.ARM64 -> {
                features.add(PlatformFeature.SIMD)
                features.add(PlatformFeature.ARM_NEON)
                features.add(PlatformFeature.AUTOVECTORIZATION)
            }
            else -> {}
        }
        
        // Check for specific libraries/tools
        if (isCommandAvailable("couchdb")) features.add(PlatformFeature.COUCHDB)
        if (isCommandAvailable("ipfs")) features.add(PlatformFeature.IPFS)
        
        return features
    }
    
    internal fun isCommandAvailable(command: String): Boolean {
        return try {
            Runtime.getRuntime().exec("which $command").waitFor() == 0
        } catch (e: Exception) {
            false
        }
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
        actual val IO_URING_MINIMUM: KernelVersion = KernelVersion(5, 1, 0)
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