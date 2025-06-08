package borg.trikeshed.net.quic.crypto

import evolution.SubtleCrypto // Assuming interface defined in evolution files
import evolution.CryptoKey    // Assuming interface defined in evolution files
import evolution.subtleCrypto // Accessing the internal val from evolution package
import evolution.toUint8Array // Assuming helper extension
import evolution.toByteArray  // Assuming helper extension
import borg.trikeshed.lib.Join // Placeholder import
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array

// subtleCrypto is imported from evolution package

actual suspend fun sha256(data: ByteArray): ByteArray {
    val resultBuffer = subtleCrypto.digest(
        js("{name: 'SHA-256'}"),
        data.toUint8Array().buffer
    ).await()
    return resultBuffer.toByteArray()
}

actual suspend fun generateEcdhKeyPair(groupId: UShort): Join<ByteArray, ByteArray> {
    val curveName = when (groupId) {
        0x001Du.toUShort() -> "X25519" // Matched to NID_X25519
        0x0017u.toUShort() -> "P-256"  // Matched to NID_X9_62_prime256v1 (secp256r1)
        // Add other curves as needed, e.g., "P-384", "P-521"
        else -> throw IllegalArgumentException("Unsupported ECDH group ID for WebCrypto: $groupId")
    }

    val keyPair = subtleCrypto.generateKey(
        js("{name: 'ECDH', namedCurve: curveName}"),
        true, // extractable (private key)
        arrayOf("deriveKey", "deriveBits")
    ).await().unsafeCast<dynamic>() // WebCrypto returns CryptoKeyPair like object

    val privateKeyCryptoKey = keyPair.privateKey.unsafeCast<CryptoKey>()
    val publicKeyCryptoKey = keyPair.publicKey.unsafeCast<CryptoKey>()

    // Export keys to raw format
    // For ECDH, public key is often uncompressed point format. Private key is scalar.
    // WebCrypto's "raw" export for EC keys can be tricky or not directly the scalar/point.
    // "spki" for public, "pkcs8" for private are more standard for export.
    // For QUIC, often raw private scalar and raw uncompressed public point are needed.
    // This part might require more platform-specific handling or a library if WebCrypto raw export isn't ideal.

    // Placeholder: Using "raw" format. This needs verification for X25519/P-256 compatibility with QUIC needs.
    val privateKeyBytes = subtleCrypto.exportKey("raw", privateKeyCryptoKey).await().toByteArray()
    val publicKeyBytes = subtleCrypto.exportKey("raw", publicKeyCryptoKey).await().toByteArray()

    return Join(privateKeyBytes, publicKeyBytes)
}

actual suspend fun computeEcdhSharedSecret(groupId: UShort, privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray {
     val curveName = when (groupId) {
        0x001Du.toUShort() -> "X25519"
        0x0017u.toUShort() -> "P-256"
        else -> throw IllegalArgumentException("Unsupported ECDH group ID for WebCrypto compute: $groupId")
    }

    // Import local private key
    val localPrivateKey = subtleCrypto.importKey(
        "raw", // This assumes privateKeyBytes is in a format WebCrypto's "raw" import understands for ECDH private keys
        privateKeyBytes.toUint8Array().buffer,
        js("{name: 'ECDH', namedCurve: curveName}"),
        false, // not extractable after import
        arrayOf("deriveBits", "deriveKey")
    ).await()

    // Import peer public key
    val peerPublicKey = subtleCrypto.importKey(
        "raw", // Assumes peerPublicKeyBytes is in a format WebCrypto's "raw" import understands
        peerPublicKeyBytes.toUint8Array().buffer,
        js("{name: 'ECDH', namedCurve: curveName}"),
        true, // Not used for derivation, but must be valid CryptoKey
        emptyArray() // No specific usages needed for public key in deriveBits
    ).await()

    // Derive shared secret (bits)
    // Length depends on the curve, e.g., 256 bits for P-256, 256 bits for X25519 (key length)
    val sharedSecretLengthBits = when(curveName) {
        "X25519" -> 256 // X25519 output is 32 bytes
        "P-256" -> 256  // P-256 shared secret is 32 bytes
        else -> 256 // Default, adjust if other curves are added
    }

    val derivedBits = subtleCrypto.deriveBits(
        js("{name: 'ECDH', public: peerPublicKey}"), // Pass peer's public CryptoKey
        localPrivateKey,                            // Local private CryptoKey
        sharedSecretLengthBits                      // Length of derived secret in bits
    ).await()

    return derivedBits.toByteArray()
}

actual suspend fun verifySignature(
    groupId: UShort,
    publicKeyBytes: ByteArray,
    signatureScheme: UShort, // e.g., 0x0804 rsa_pss_rsae_sha256, 0x0403 ecdsa_secp256r1_sha256
    dataToVerify: ByteArray,
    signature: ByteArray
): Boolean {
    // Mapping signatureScheme to WebCrypto algorithm objects is complex.
    // This requires knowing key type (RSA, EC), curve, hash, padding (for RSA-PSS).

    // Example for ECDSA P-256 SHA-256 (0x0403)
    val algorithm: dynamic
    val keyFormat: String = "spki" // Standard format for public keys, or "raw" if bytes are just the point
    var keyData: ArrayBuffer = publicKeyBytes.toUint8Array().buffer

    when (signatureScheme) {
        0x0403u.toUShort() -> { // ecdsa_secp256r1_sha256
            algorithm = js("{name: 'ECDSA', hash: 'SHA-256', namedCurve: 'P-256'}")
            // For "raw" import of P-256 public key, it needs to be uncompressed point.
            // If publicKeyBytes is already this, keyFormat = "raw". Otherwise, SPKI is more common.
        }
        0x0804u.toUShort() -> { // rsa_pss_rsae_sha256
             algorithm = js("{name: 'RSA-PSS', hash: 'SHA-256', saltLength: 32}") // saltLength for SHA-256 is 32
             // RSA public key usually in SPKI format.
        }
        // Add more schemes: e.g., Ed25519 (0x0807) -> {name: "Ed25519"} (no hash needed in algo obj)
        else -> throw IllegalArgumentException("Unsupported signature scheme for WebCrypto verify: $signatureScheme")
    }

    try {
        val cryptoKey = subtleCrypto.importKey(
            keyFormat, // "spki" or "raw" (if raw, ensure format is correct for EC point)
            keyData,
            algorithm, // Algorithm object for importKey should match verify algo's key type part
            true, // extractable: false, as we only use it for verify
            arrayOf("verify")
        ).await()

        return subtleCrypto.verify(
            algorithm, // Algorithm for verify (includes hash, padding etc.)
            cryptoKey,
            signature.toUint8Array().buffer,
            dataToVerify.toUint8Array().buffer
        ).await()
    } catch (e: dynamic) {
        // Verification can fail due to various reasons (bad signature, key format error)
        // console.error("Signature verification error:", e) // For debugging
        return false
    }
}
