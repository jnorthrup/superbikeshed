package borg.trikeshed.lib.platform

/**
 * JS implementation of platform detection
 * 
 * Note: JS environment has limited access to system information,
 * so this implementation provides basic platform detection.
 */
actual object PlatformDetection {
    
    internal var cachedInfo: PlatformInfo? = null
    
    actual fun getPlatformInfo(): PlatformInfo {
        return cachedInfo ?: detectPlatform().also { cachedInfo = it }
    }
    
    actual fun hasIoUringSupport(): Boolean {
        // io_uring is not available in JS environment
        return false
    }
    
    actual fun hasFeature(feature: PlatformFeature): Boolean {
        return getPlatformInfo().features.contains(feature)
    }
    
    actual fun getKernelVersion(): KernelVersion? {
        // Kernel version is not available in JS environment
        return null
    }
    
    /**
     * Detect platform information
     */
    internal fun detectPlatform(): PlatformInfo {
        val os = detectOperatingSystem()
        val arch = detectArchitecture()
        val features = detectFeatures(os, arch)
        
        return PlatformInfo(os, arch, null, features)
    }
    
    /**
     * Detect operating system
     */
    internal fun detectOperatingSystem(): OperatingSystem {
        // Use navigator.platform or userAgent for OS detection
        val platform = kotlinx.browser.window.navigator.platform.lowercase()
        val userAgent = kotlinx.browser.window.navigator.userAgent.lowercase()
        
        return when {
            platform.contains("linux") || userAgent.contains("linux") -> OperatingSystem.LINUX
            platform.contains("mac") || userAgent.contains("mac") -> OperatingSystem.MACOS
            platform.contains("win") || userAgent.contains("windows") -> OperatingSystem.WINDOWS
            platform.contains("android") || userAgent.contains("android") -> OperatingSystem.ANDROID
            platform.contains("ios") || userAgent.contains("iphone") || userAgent.contains("ipad") -> OperatingSystem.IOS
            else -> OperatingSystem.UNKNOWN
        }
    }
    
    /**
     * Detect CPU architecture
     */
    internal fun detectArchitecture(): Architecture {
        // JS environment typically runs on the architecture of the host
        // We can't reliably detect this, so return UNKNOWN
        return Architecture.UNKNOWN
    }
    
    /**
     * Detect platform features
     */
    internal fun detectFeatures(
        os: OperatingSystem, 
        arch: Architecture
    ): Set<PlatformFeature> {
        val features = mutableSetOf<PlatformFeature>()
        
        // JS-specific features
        features.add(PlatformFeature.VARIABLE_LENGTH)
        
        // OS-specific features (limited in JS)
        when (os) {
            OperatingSystem.LINUX -> {
                // Basic features that might be available
                features.add(PlatformFeature.ZERO_COPY)
            }
            OperatingSystem.MACOS -> {
                features.add(PlatformFeature.ZERO_COPY)
            }
            else -> {
                // Generic features for other platforms
            }
        }
        
        return features
    }
}

actual class PlatformInfo actual constructor(
    os: OperatingSystem,
    arch: Architecture,
    kernelVersion: KernelVersion?,
    features: Set<PlatformFeature>
) {
    actual val os: OperatingSystem = os
    actual val arch: Architecture = arch
    actual val kernelVersion: KernelVersion? = kernelVersion
    actual val features: Set<PlatformFeature> = features
}

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
    major: Int,
    minor: Int,
    patch: Int,
    extra: String
) : Comparable<KernelVersion> {
    actual val major: Int = major
    actual val minor: Int = minor
    actual val patch: Int = patch
    actual val extra: String = extra

    actual companion object {
        actual val IO_URING_MINIMUM: KernelVersion = KernelVersion(0, 0, 0) // Dummy for JS
    }

    override fun compareTo(other: KernelVersion): Int {
        // Dummy comparison for JS
        return 0
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
