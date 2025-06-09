package borg.trikeshed.net.quic.crypto

import borg.trikeshed.net.quic.tls.P256_GROUP
import borg.trikeshed.net.quic.tls.X25519_GROUP
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

actual fun generateEcdhKeyPair(groupId: UShort): Pair<ByteArray, ByteArray> {
    val algorithm: String
    val keyGenParameterSpec: java.security.spec.AlgorithmParameterSpec?

    when (groupId) {
        X25519_GROUP -> {
            // For X25519, modern JDKs (11+) support "XDH" KeyPairGenerator and "X25519" KeyAgreement
            // However, to ensure broader compatibility or if specific X25519 handling is needed,
            // one might use BouncyCastle or a similar library.
            // Here we assume JDK 11+ "X25519" KeyPairGenerator.
            try {
                val kpg = KeyPairGenerator.getInstance("X25519")
                // X25519 does not need ECGenParameterSpec
                val keyPair = kpg.generateKeyPair()
                return Pair(keyPair.private.encoded, keyPair.public.encoded)
            } catch (e: Exception) {
                // Fallback or specific error handling if X25519 direct is not available
                // For simplicity, rethrowing. A real app might try BouncyCastle or other curves.
                throw UnsupportedOperationException("X25519 key pair generation failed, consider JDK 11+ or BouncyCastle.", e)
            }
        }
        P256_GROUP -> {
            algorithm = "EC"
            keyGenParameterSpec = ECGenParameterSpec("secp256r1") // NIST P-256
        }
        // Add other groups as needed, e.g., P384_GROUP -> "secp384r1"
        else -> throw IllegalArgumentException("Unsupported ECDH group ID: $groupId")
    }

    val kpg = KeyPairGenerator.getInstance(algorithm)
    if (keyGenParameterSpec != null) {
        kpg.initialize(keyGenParameterSpec)
    }
    val keyPair = kpg.generateKeyPair()
    return Pair(keyPair.private.encoded, keyPair.public.encoded)
}

actual fun computeEcdhSharedSecret(
    groupId: UShort,
    privateKeyBytes: ByteArray,
    peerPublicKeyBytes: ByteArray
): ByteArray {
    val keyFactoryAlgorithm: String
    val keyAgreementAlgorithm: String

    when (groupId) {
        X25519_GROUP -> {
            // Using "XDH" KeyFactory and "X25519" KeyAgreement for X25519 if available (JDK 11+)
            keyFactoryAlgorithm = "XDH"
            keyAgreementAlgorithm = "X25519"
             try {
                val kf = KeyFactory.getInstance(keyFactoryAlgorithm)
                val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
                val localPrivateKey: PrivateKey = kf.generatePrivate(privateKeySpec)

                val publicKeySpec = X509EncodedKeySpec(peerPublicKeyBytes)
                val peerPublicKey: PublicKey = kf.generatePublic(publicKeySpec)

                val ka = KeyAgreement.getInstance(keyAgreementAlgorithm)
                ka.init(localPrivateKey)
                ka.doPhase(peerPublicKey, true)
                return ka.generateSecret()
            } catch (e: Exception) {
                 throw UnsupportedOperationException("X25519 shared secret computation failed, consider JDK 11+ or BouncyCastle.", e)
            }
        }
        P256_GROUP -> {
            keyFactoryAlgorithm = "EC"
            keyAgreementAlgorithm = "ECDH"
        }
        // Add other groups as needed
        else -> throw IllegalArgumentException("Unsupported ECDH group ID: $groupId")
    }

    val kf = KeyFactory.getInstance(keyFactoryAlgorithm)

    val privateKeySpec = PKCS8EncodedKeySpec(privateKeyBytes)
    val localPrivateKey: PrivateKey = kf.generatePrivate(privateKeySpec)

    val publicKeySpec = X509EncodedKeySpec(peerPublicKeyBytes)
    val peerPublicKey: PublicKey = kf.generatePublic(publicKeySpec)

    val ka = KeyAgreement.getInstance(keyAgreementAlgorithm)
    ka.init(localPrivateKey)
    ka.doPhase(peerPublicKey, true)
    return ka.generateSecret()
}

actual fun sha256(data: ByteArray): ByteArray {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(data)
}

actual fun verifySignature(
    groupId: UShort,
    publicKeyBytes: ByteArray,
    signatureScheme: UShort, // e.g., TlsSignatureScheme.ecdsa_secp256r1_sha256
    dataToVerify: ByteArray,
    signature: ByteArray
): Boolean {
    // This is a placeholder implementation.
    // A real implementation would involve:
    // 1. Parsing publicKeyBytes into a java.security.PublicKey object.
    //    - This depends on the key type (RSA, EC, EdDSA). For EC keys, groupId might be relevant.
    //    - Example for EC: KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeyBytes))
    // 2. Mapping signatureScheme to JCA algorithm names (e.g., "SHA256withECDSA", "SHA256withRSAandMGF1").
    //    - This mapping is non-trivial. See TlsSignatureScheme object for constants.
    // 3. Initializing a java.security.Signature object with the algorithm and public key.
    //    - `val verifier = java.security.Signature.getInstance(jcaAlgorithmName)`
    //    - `verifier.initVerify(publicKey)`
    //    - If PSS (like rsa_pss_rsae_sha256), PSSParameterSpec needs to be configured.
    // 4. Calling `verifier.update(dataToVerify)`.
    // 5. Calling `verifier.verify(signature)`.

    // For now, as per subtask instructions:
    // println("Warning: PlatformCrypto.jvm.kt verifySignature is a placeholder and always returns true.")
    // return true

    val keyFactoryAlgorithm: String
    val jcaSignatureAlgorithmName: String

    // Map TlsSignatureScheme to JCA KeyFactory and Signature algorithm names
    // This is a simplified mapping. A comprehensive one would be larger.
    // See RFC 8446 Appendix C for JCA names.
    when (signatureScheme) {
        borg.trikeshed.net.quic.tls.TlsSignatureScheme.ECDSA_SECP256R1_SHA256 -> {
            keyFactoryAlgorithm = "EC"
            jcaSignatureAlgorithmName = "SHA256withECDSA"
        }
        borg.trikeshed.net.quic.tls.TlsSignatureScheme.RSA_PSS_RSAE_SHA256 -> {
            keyFactoryAlgorithm = "RSA"
            jcaSignatureAlgorithmName = "SHA256withRSA/PSS"
            // For PSS, specific PSSParameterSpec might be needed for initSign/initVerify if not using defaults.
            // However, for verification, often defaults or key-embedded params are enough.
        }
        borg.trikeshed.net.quic.tls.TlsSignatureScheme.ED25519 -> {
            // EdDSA keys might need BouncyCastle or JDK 11+ "EdDSA" KeyFactory.
            // For simplicity, we'll assume it's available if this scheme is used.
            keyFactoryAlgorithm = "Ed25519" // Or "EdDSA"
            jcaSignatureAlgorithmName = "Ed25519" // Or "EdDSA"
        }
        // Add more schemes as needed
        else -> {
            println("Warning: Unsupported signature scheme in verifySignature: $signatureScheme")
            return false // Unsupported scheme
        }
    }

    return try {
        val kf = java.security.KeyFactory.getInstance(keyFactoryAlgorithm)
        val pubKeySpec = java.security.spec.X509EncodedKeySpec(publicKeyBytes) // Assumes SPKI format
        val publicKey = kf.generatePublic(pubKeySpec)

        val sig = java.security.Signature.getInstance(jcaSignatureAlgorithmName)

        // Special handling for PSS if needed (e.g. setting PSSParameterSpec)
        // if (jcaSignatureAlgorithmName.endsWith("RSA/PSS")) {
        //     val pssParams = java.security.spec.PSSParameterSpec(...)
        //     sig.setParameter(pssParams)
        // }

        sig.initVerify(publicKey)
        sig.update(dataToVerify)
        sig.verify(signature)
    } catch (e: java.security.NoSuchAlgorithmException) {
        println("Signature verification error: Algorithm not found - ${e.message}")
        false
    } catch (e: java.security.spec.InvalidKeySpecException) {
        println("Signature verification error: Invalid key spec - ${e.message}")
        false
    } catch (e: java.security.InvalidKeyException) {
        println("Signature verification error: Invalid key - ${e.message}")
        false
    } catch (e: java.security.SignatureException) {
        println("Signature verification error: Signature exception - ${e.message}")
        false // Includes actual signature mismatch
    } catch (e: Exception) {
        println("Signature verification error: Unexpected exception - ${e.message}")
        false
    }
}
