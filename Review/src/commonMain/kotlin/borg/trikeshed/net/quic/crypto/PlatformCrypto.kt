package borg.trikeshed.net.quic.crypto

// groupId: e.g., X25519_GROUP (0x001Du) or P256_GROUP (0x0017u)
// Returns Pair(privateKey, publicKey)
expect fun generateEcdhKeyPair(groupId: UShort): Pair<ByteArray, ByteArray>

// groupId: Specifies the elliptic curve group.
// privateKey: The local private key.
// peerPublicKey: The public key received from the peer.
// Returns the computed shared secret.
expect fun computeEcdhSharedSecret(groupId: UShort, privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray

/**
 * Computes the SHA-256 hash of the input data.
 * @param data The input byte array.
 * @return A byte array representing the SHA-256 hash (32 bytes).
 */
expect fun sha256(data: ByteArray): ByteArray

/**
 * Verifies a digital signature.
 *
 * @param groupId The curve group ID, relevant if the public key or signature scheme implies a specific curve (e.g. for ECDSA).
 *                May not be strictly needed if PublicKey object itself contains all necessary info or if scheme is RSA.
 * @param publicKeyBytes The encoded public key used for verification.
 * @param signatureScheme The TlsSignatureScheme (e.g., rsa_pss_rsae_sha256, ecdsa_secp256r1_sha256).
 * @param dataToVerify The original data that was signed.
 * @param signature The signature to verify.
 * @return True if the signature is valid, false otherwise.
 */
expect fun verifySignature(
    groupId: UShort, // May not be needed if public key self-describes or scheme is not EC
    publicKeyBytes: ByteArray,
    signatureScheme: UShort,
    dataToVerify: ByteArray,
    signature: ByteArray
): Boolean


// Helper data class for storing derived secrets.
// In a real implementation, this might also store cipher-specific details if not uniform.
data class QuicSecrets(
    val key: ByteArray,
    val iv: ByteArray,
    val hpKey: ByteArray // Header Protection Key
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as QuicSecrets

        if (!key.contentEquals(other.key)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (!hpKey.contentEquals(other.hpKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + hpKey.contentHashCode()
        return result
    }
}
