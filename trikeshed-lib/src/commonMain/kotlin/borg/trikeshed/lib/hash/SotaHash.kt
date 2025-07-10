package borg.trikeshed.lib.hash

/**
 * SOTA Hash API for TrikeShed
 *
 * Provides a unified interface for state-of-the-art cryptographic and fast non-cryptographic hashes.
 *
 * Supported algorithms:
 * - BLAKE3 (cryptographic, SIMD, fast)
 * - SHA-256 (cryptographic, standard)
 * - SHA3-256 (cryptographic, modern)
 * - BLAKE2b-256 (cryptographic, fast)
 * - xxHash64 (non-cryptographic, very fast)
 */

enum class SotaHashType {
    BLAKE3, SHA256, SHA3_256, BLAKE2B_256, XXHASH64
}

interface SotaHasher {
    fun hash(data: ByteArray): ByteArray
}

object SotaHashers {
    val blake3: SotaHasher = Blake3Hasher()
    val sha256: SotaHasher = Sha256Hasher()
    val sha3_256: SotaHasher = Sha3_256Hasher()
    val blake2b_256: SotaHasher = Blake2b256Hasher()
    val xxhash64: SotaHasher = XxHash64Hasher()

    fun get(type: SotaHashType): SotaHasher = when (type) {
        SotaHashType.BLAKE3 -> blake3
        SotaHashType.SHA256 -> sha256
        SotaHashType.SHA3_256 -> sha3_256
        SotaHashType.BLAKE2B_256 -> blake2b_256
        SotaHashType.XXHASH64 -> xxhash64
    }
}

// === SOTA Hash Implementations (stubs, platform-specific impls should override) ===

class Blake3Hasher : SotaHasher {
    override fun hash(data: ByteArray): ByteArray {
        // TODO: Implement BLAKE3 (use platform-specific bindings or pure Kotlin)
        throw NotImplementedError("BLAKE3 hash not implemented yet")
    }
}

class Sha256Hasher : SotaHasher {
    override fun hash(data: ByteArray): ByteArray {
        // TODO: Implement SHA-256 (use java.security or multiplatform)
        throw NotImplementedError("SHA-256 hash not implemented yet")
    }
}

class Sha3_256Hasher : SotaHasher {
    override fun hash(data: ByteArray): ByteArray {
        // TODO: Implement SHA3-256 (use BouncyCastle or multiplatform)
        throw NotImplementedError("SHA3-256 hash not implemented yet")
    }
}

class Blake2b256Hasher : SotaHasher {
    override fun hash(data: ByteArray): ByteArray {
        // TODO: Implement BLAKE2b-256 (use BouncyCastle or multiplatform)
        throw NotImplementedError("BLAKE2b-256 hash not implemented yet")
    }
}

class XxHash64Hasher : SotaHasher {
    override fun hash(data: ByteArray): ByteArray {
        // TODO: Implement xxHash64 (use pure Kotlin or JNI)
        throw NotImplementedError("xxHash64 hash not implemented yet")
    }
}

/**
 * Utility for anchor key and content addressing hash
 */
fun anchorKeyHash(
    inferences: List<Triple<Int, Int, Int>>,
    hashType: SotaHashType = SotaHashType.BLAKE3
): ByteArray {
    val canonical = inferences.sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
    val byteList = mutableListOf<Byte>()
    canonical.forEach { t ->
        listOf(t.first, t.second, t.third).forEach { n ->
            n.toString().encodeToByteArray().forEach { b -> byteList.add(b) }
        }
    }
    val bytes = ByteArray(byteList.size)
    for (i in byteList.indices) {
        bytes[i] = byteList[i]
    }
    return SotaHashers.get(hashType).hash(bytes)
} 