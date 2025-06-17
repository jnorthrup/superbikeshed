package gk.kademlia.security

import kotlin.random.Random

// --- Dummy PublicKey implementation ---
data class DummyPublicKey(val keyData: ByteArray) : PublicKey {
    override fun getEncoded(): ByteArray = keyData
    override fun equals(other: Any?): Boolean { // Recommended for data classes with ByteArray
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DummyPublicKey
        if (!keyData.contentEquals(other.keyData)) return false
        return true
    }
    override fun hashCode(): Int { // Recommended for data classes with ByteArray
        return keyData.contentHashCode()
    }
}

// --- Dummy PrivateKey implementation ---
data class DummyPrivateKey(val keyData: ByteArray) : PrivateKey {
    override fun getEncoded(): ByteArray = keyData
     override fun equals(other: Any?): Boolean { // Recommended for data classes with ByteArray
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DummyPrivateKey
        if (!keyData.contentEquals(other.keyData)) return false
        return true
    }
    override fun hashCode(): Int { // Recommended for data classes with ByteArray
        return keyData.contentHashCode()
    }
}

class DummyCryptoService : CryptoService {

    override fun generateKeyPair(): KeyPair {
        println("DummyCryptoService: Faking key pair generation.")
        val publicKeyBytes = "dummy_public_key_${Random.nextInt()}".encodeToByteArray()
        val privateKeyBytes = "dummy_private_key_${Random.nextInt()}".encodeToByteArray()
        return KeyPair(DummyPublicKey(publicKeyBytes), DummyPrivateKey(privateKeyBytes))
    }

    override fun hash(data: ByteArray, algorithm: String): ByteArray {
        println("DummyCryptoService: Faking hash of data with algorithm: $algorithm.")
        // Simple transform: take first 32 bytes or pad with index
        val result = ByteArray(32) { i -> data.getOrNull(i) ?: i.toByte() }
        println("DummyCryptoService: Input data (first 10 bytes): ${data.take(10).joinToString()}, Output hash (first 10 bytes): ${result.take(10).joinToString()}")
        return result
    }

    override fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray {
        println("DummyCryptoService: Faking signature for data (first 10 bytes): ${data.take(10).joinToString()} using private key: ${(privateKey as DummyPrivateKey).keyData.decodeToString().take(10)}...")
        return "fakesig_for_${data.take(5).map { it.toInt().and(0xFF) }.joinToString("-")}".encodeToByteArray()
    }

    override fun verifySignature(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Boolean {
        println("DummyCryptoService: Faking signature verification for data (first 10 bytes): ${data.take(10).joinToString()}, signature: ${signature.decodeToString().take(10)}..., publicKey: ${(publicKey as DummyPublicKey).keyData.decodeToString().take(10)}...")
        // Simple check: if signature starts with "fakesig_for_"
        return signature.decodeToString().startsWith("fakesig_for_")
    }

    override fun encodePublicKey(publicKey: PublicKey): ByteArray {
        require(publicKey is DummyPublicKey) { "Unsupported PublicKey type" }
        println("DummyCryptoService: Encoding PublicKey: ${publicKey.keyData.decodeToString().take(10)}...")
        return publicKey.keyData
    }

    override fun decodePublicKey(encoded: ByteArray): PublicKey {
        println("DummyCryptoService: Decoding PublicKey from data (first 10 bytes): ${encoded.take(10).joinToString()}")
        return DummyPublicKey(encoded)
    }
}
