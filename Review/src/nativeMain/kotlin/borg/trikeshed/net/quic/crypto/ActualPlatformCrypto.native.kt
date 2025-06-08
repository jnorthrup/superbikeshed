package borg.trikeshed.net.quic.crypto

import kotlinx.cinterop.*
import platform.openssl.*
import kotlinx.coroutines.Dispatchers
import borg.trikeshed.lib.Join // Placeholder import
import kotlinx.coroutines.withContext


actual suspend fun sha256(data: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    memScoped {
        val dataPtr = data.pin()
        val mdLen = alloc<UIntVar>()
        val mdBuf = allocArray<UByteVar>(EVP_MAX_MD_SIZE)

        val ctx = EVP_MD_CTX_new() ?: throw RuntimeException("EVP_MD_CTX_new failed")
        try {
            if (EVP_DigestInit_ex(ctx, EVP_sha256(), null) != 1) throw RuntimeException("EVP_DigestInit_ex failed")
            if (EVP_DigestUpdate(ctx, dataPtr.addressOf(0), data.size.convert()) != 1) throw RuntimeException("EVP_DigestUpdate failed")
            if (EVP_DigestFinal_ex(ctx, mdBuf, mdLen.ptr) != 1) throw RuntimeException("EVP_DigestFinal_ex failed")
        } finally {
            EVP_MD_CTX_free(ctx)
            dataPtr.unpin()
        }
        return@withContext mdBuf.readBytes(mdLen.value.toInt())
    }
}

// Note: OpenSSL key generation and ECDH are complex. This is a simplified outline.
// Proper error handling and parameter setup (e.g., for specific curves like X25519) is crucial.
actual suspend fun generateEcdhKeyPair(groupId: UShort): Join<ByteArray, ByteArray> = withContext(Dispatchers.Default) {
    // groupId mapping to NID, e.g., X25519_GROUP (0x001Du) -> NID_X25519
    // For simplicity, assuming X25519 if groupId indicates it, otherwise not implemented
    val pkeyNid = when(groupId) {
        0x001Du.toUShort() -> NID_X25519 // TLS_GROUP_X25519
        // Add other groups like NID_X448, NID_secp256r1 (aka prime256v1) etc.
        else -> throw IllegalArgumentException("Unsupported ECDH group ID: $groupId")
    }

    var pctx: CValuesRef<EVP_PKEY_CTX>? = null
    var pkey: CValuesRef<EVP_PKEY>? = null
    try {
        pctx = EVP_PKEY_CTX_new_id(pkeyNid, null) ?: throw RuntimeException("EVP_PKEY_CTX_new_id failed for keygen")
        if (EVP_PKEY_keygen_init(pctx) <= 0) throw RuntimeException("EVP_PKEY_keygen_init failed")
        // For specific curves like P-256, might need: EVP_PKEY_CTX_set_ec_paramgen_curve_nid(pctx, NID_X25519);

        if (EVP_PKEY_keygen(pctx, cValuesRef(EVP_PKEY_ptr_create())) <= 0) { // Pass pkey by reference
             throw RuntimeException("EVP_PKEY_keygen failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")
        }
        pkey = EVP_PKEY_ptr_get(cValuesRef(EVP_PKEY_ptr_create())) // Retrieve the generated pkey


        // Get private key bytes
        val privKeyLen = alloc<size_tVar>()
        if (EVP_PKEY_get_raw_private_key(pkey, null, privKeyLen.ptr) != 1 && ERR_peek_last_error() != 0u) {
             // Some key types might not support raw private key easily or require different functions.
             // For X25519, this should work.
        }
        val privKeyBuf = allocArray<UByteVar>(privKeyLen.value.toInt())
        if (EVP_PKEY_get_raw_private_key(pkey, privKeyBuf, privKeyLen.ptr) != 1) throw RuntimeException("EVP_PKEY_get_raw_private_key failed")
        val privateKeyBytes = privKeyBuf.readBytes(privKeyLen.value.toInt())

        // Get public key bytes
        val pubKeyLen = alloc<size_tVar>()
        if (EVP_PKEY_get_raw_public_key(pkey, null, pubKeyLen.ptr) != 1 && ERR_peek_last_error() != 0u) {}

        val pubKeyBuf = allocArray<UByteVar>(pubKeyLen.value.toInt())
        if (EVP_PKEY_get_raw_public_key(pkey, pubKeyBuf, pubKeyLen.ptr) != 1) throw RuntimeException("EVP_PKEY_get_raw_public_key failed")
        val publicKeyBytes = pubKeyBuf.readBytes(pubKeyLen.value.toInt())

        return@withContext Join(privateKeyBytes, publicKeyBytes)
    } finally {
        EVP_PKEY_free(pkey)
        EVP_PKEY_CTX_free(pctx)
    }
}


actual suspend fun computeEcdhSharedSecret(groupId: UShort, privateKeyBytes: ByteArray, peerPublicKeyBytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
    memScoped {
        val pkeyNid = when(groupId) {
            0x001Du.toUShort() -> NID_X25519
            else -> throw IllegalArgumentException("Unsupported ECDH group ID for compute: $groupId")
        }

        var localPkey: CValuesRef<EVP_PKEY>? = null
        var peerPkey: CValuesRef<EVP_PKEY>? = null
        var dctx: CValuesRef<EVP_PKEY_CTX>? = null
        try {
            val privPinned = privateKeyBytes.pin()
            localPkey = EVP_PKEY_new_raw_private_key(pkeyNid, null, privPinned.addressOf(0).reinterpret(), privateKeyBytes.size.convert())
            privPinned.unpin()
            if (localPkey == null) throw RuntimeException("EVP_PKEY_new_raw_private_key failed for local key")

            val peerPubPinned = peerPublicKeyBytes.pin()
            peerPkey = EVP_PKEY_new_raw_public_key(pkeyNid, null, peerPubPinned.addressOf(0).reinterpret(), peerPublicKeyBytes.size.convert())
            peerPubPinned.unpin()
            if (peerPkey == null) throw RuntimeException("EVP_PKEY_new_raw_public_key failed for peer key")

            dctx = EVP_PKEY_CTX_new(localPkey, null) ?: throw RuntimeException("EVP_PKEY_CTX_new failed for derivation")
            if (EVP_PKEY_derive_init(dctx) <= 0) throw RuntimeException("EVP_PKEY_derive_init failed")
            if (EVP_PKEY_derive_set_peer(dctx, peerPkey) <= 0) throw RuntimeException("EVP_PKEY_derive_set_peer failed")

            val secretLen = alloc<size_tVar>()
            if (EVP_PKEY_derive(dctx, null, secretLen.ptr) <= 0) throw RuntimeException("EVP_PKEY_derive length check failed")

            val secretBuf = allocArray<UByteVar>(secretLen.value.toInt())
            if (EVP_PKEY_derive(dctx, secretBuf, secretLen.ptr) <= 0) throw RuntimeException("EVP_PKEY_derive failed: ${ERR_error_string(ERR_get_error(), null)?.toKString()}")

            return@withContext secretBuf.readBytes(secretLen.value.toInt())
        } finally {
            EVP_PKEY_free(localPkey)
            EVP_PKEY_free(peerPkey)
            EVP_PKEY_CTX_free(dctx)
        }
    }
}

actual suspend fun verifySignature(
    groupId: UShort, // May hint at key type (e.g. EC curve if NID needed for EVP_PKEY_verify_init)
    publicKeyBytes: ByteArray,
    signatureScheme: UShort, // e.g., 0x0804 for rsa_pss_rsae_sha256, 0x0403 for ecdsa_secp256r1_sha256
    dataToVerify: ByteArray,
    signature: ByteArray
): Boolean = withContext(Dispatchers.Default) {
    // This is a complex function due to mapping signatureScheme to OpenSSL types.
    // Simplified example assuming RSA PSS SHA256 or ECDSA P256 SHA256
    memScoped {
        var pkey: CValuesRef<EVP_PKEY>? = null
        var mctx: CValuesRef<EVP_MD_CTX>? = null
        val pubKeyPinned = publicKeyBytes.pin()
        val dataPtr = dataToVerify.pin()
        val sigPtr = signature.pin()

        try {
            // Convert raw public key bytes to EVP_PKEY. This is highly dependent on key format (SPKI DER, raw, etc.)
            // For simplicity, assume SPKI DER format that d2i_PUBKEY can parse.
            // Or, if raw public key, one needs to know the type (RSA, EC) and curve.
            // Example for SPKI:
            // val pPubKey = cValuesOf(pubKeyPinned.addressOf(0).reinterpret<UByteVar>())
            // pkey = d2i_PUBKEY(null, pPubKey?.getPointer(this), publicKeyBytes.size.convert())
            // For now, we'll need a more specific way to load the key based on type.
            // This part is highly dependent on how publicKeyBytes are encoded.
            // Let's assume for X25519/Ed25519 (though X25519 is for DH, Ed25519 for sig):
            // pkey = EVP_PKEY_new_raw_public_key(NID_ed25519, null, pubKeyPinned.addressOf(0).reinterpret(), publicKeyBytes.size.convert())
            // This is a placeholder for proper public key loading based on signatureScheme.

            // For now, this function will be a placeholder returning false until key loading is robust.
            // throw NotImplementedError("Signature verification with OpenSSL's EVP_PKEY requires robust public key parsing based on scheme.")

            // Conceptual steps if pkey is loaded:
            // mctx = EVP_MD_CTX_new() ?: throw RuntimeException("EVP_MD_CTX_new for verify failed")
            // val md = when (signatureScheme) { // Map to EVP_MD based on signatureScheme hash component
            //    0x0804u.toUShort() /* rsa_pss_rsae_sha256 */ -> EVP_sha256()
            //    0x0403u.toUShort() /* ecdsa_secp256r1_sha256 */ -> EVP_sha256()
            //    else -> throw IllegalArgumentException("Unsupported signature scheme for verify: $signatureScheme")
            // }
            // if (EVP_DigestVerifyInit(mctx, null, md, null, pkey) != 1) throw RuntimeException("EVP_DigestVerifyInit failed")
            // if (signatureScheme == 0x0804u.toUShort()) { // RSA PSS specific padding if needed
            //    val pctx = EVP_MD_CTX_pkey_ctx(mctx)
            //    if (EVP_PKEY_CTX_set_rsa_padding(pctx, RSA_PKCS1_PSS_PADDING) <= 0) throw RuntimeException("Failed to set RSA PSS padding")
            //    if (EVP_PKEY_CTX_set_rsa_pss_saltlen(pctx, -1 /* RSA_PSS_SALTLEN_DIGEST */) <= 0) throw RuntimeException("Failed to set RSA PSS saltlen")
            // }
            // if (EVP_DigestVerifyUpdate(mctx, dataPtr.addressOf(0), dataToVerify.size.convert()) != 1) throw RuntimeException("EVP_DigestVerifyUpdate failed")
            // val result = EVP_DigestVerifyFinal(mctx, sigPtr.addressOf(0).reinterpret(), signature.size.convert())
            // return@withContext result == 1

            return@withContext false // Placeholder until key loading from bytes is fully specified
        } finally {
            // EVP_PKEY_free(pkey)
            // EVP_MD_CTX_free(mctx)
            pubKeyPinned.unpin()
            dataPtr.unpin()
            sigPtr.unpin()
        }
    }
}
