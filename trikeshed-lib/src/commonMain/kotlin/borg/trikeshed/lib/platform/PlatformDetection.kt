package borg.trikeshed.lib.platform

expect object PlatformDetection {
    fun getPlatformInfo(): PlatformInfo
    fun hasIoUringSupport(): Boolean
    fun hasFeature(feature: PlatformFeature): Boolean
    fun getKernelVersion(): KernelVersion?
}

expect class PlatformInfo(
    os: OperatingSystem,
    arch: Architecture,
    kernelVersion: KernelVersion?,
    features: Set<PlatformFeature>
) {
    val os: OperatingSystem
    val arch: Architecture
    val kernelVersion: KernelVersion?
    val features: Set<PlatformFeature>
}

expect enum class OperatingSystem {
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

expect enum class Architecture {
    X64,
    ARM64,
    ARM32,
    RISCV64,
    WASM,
    UNKNOWN
}

expect class KernelVersion(
    major: Int,
    minor: Int,
    patch: Int,
    extra: String = ""
) : Comparable<KernelVersion> {
    val major: Int
    val minor: Int
    val patch: Int
    val extra: String
    
    override fun compareTo(other: KernelVersion): Int

    companion object {
        val IO_URING_MINIMUM: KernelVersion
    }
}

expect enum class PlatformFeature {
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