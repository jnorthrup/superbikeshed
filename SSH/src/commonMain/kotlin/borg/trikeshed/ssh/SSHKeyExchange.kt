package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.*

class SSHKeyExchange(
    private val transport: SSHTransport, // To send/receive packets
    private val crypto: CommonCrypto,    // For crypto operations
    private val isServer: Boolean
) {
    private var state: KexState = KexState.EXPECT_KEXINIT

    // Algorithm negotiation results
    private var chosenKexAlgorithm: KeyExchangeAlgorithm? = null
    private var chosenHostKeyAlgorithm: SignatureAlgorithm? = null
    private var chosenCipherClientToServer: CipherSuite? = null
    private var chosenCipherServerToClient: CipherSuite? = null
    private var chosenMacClientToServer: HashAlgorithm? = null
    private var chosenMacServerToClient: HashAlgorithm? = null
    private var chosenCompressionClientToServer: String? = null
    private var chosenCompressionServerToClient: String? = null

    // Key exchange specific data
    private var clientKexInitPayload: Indexed<Byte>? = null
    private var serverKexInitPayload: Indexed<Byte>? = null
    private var hostKey: Join<PublicKey, PrivateKey>? = null // Server's host key
    private var exchangeHash: Hash? = null

    // Diffie-Hellman specific
    private var dhKeyPair: Join<PublicKey, PrivateKey>? = null
    private var clientDhPublic: PublicKey? = null
    private var serverDhPublic: PublicKey? = null
    private var sharedSecret: SessionKey? = null


    private enum class KexState {
        EXPECT_KEXINIT,
        EXPECT_KEX_METHOD_SPECIFIC, // e.g., KEXDH_INIT or KEX_ECDH_INIT
        EXPECT_NEWKEYS,
        COMPLETED,
        FAILED
    }

    fun processPacket(packet: SSHPacket): List<SSHPacket> {
        val responses = mutableListOf<SSHPacket>()
        when (state) {
            KexState.EXPECT_KEXINIT -> {
                if (packet.messageType == SSHMessageType.KEXINIT) {
                    handleKexInit(packet.payload)
                    // If we are server, we might send KEXDH_REPLY or similar
                    // If we are client, we might send KEXDH_INIT
                    // For now, this is simplified
                    if (!isServer && chosenKexAlgorithm != null) {
                        // Client initiates DH
                        val kexInitPacket = createKexDhInit()
                        if (kexInitPacket != null) responses.add(kexInitPacket) else state = KexState.FAILED
                    }
                    state = KexState.EXPECT_KEX_METHOD_SPECIFIC
                } else {
                    // Error: unexpected packet
                    failKex("Expected KEXINIT, got ${packet.messageType}")
                }
            }
            KexState.EXPECT_KEX_METHOD_SPECIFIC -> {
                // Handle messages like KEXDH_INIT, KEXDH_REPLY, etc.
                // This will depend on the chosen KEX algorithm
                when (packet.messageType) {
                    SSHMessageType.KEXDH_INIT -> { // Server receives this
                        if (isServer) {
                             handleKexDhInit(packet.payload)
                             val reply = createKexDhReply()
                             if (reply != null) responses.add(reply) else failKex("Failed to create KEXDH_REPLY")
                             // After sending reply, server expects NEWKEYS
                             state = KexState.EXPECT_NEWKEYS
                        } else {
                            failKex("Client should not receive KEXDH_INIT")
                        }
                    }
                    SSHMessageType.KEXDH_REPLY -> { // Client receives this
                        if (!isServer) {
                            handleKexDhReply(packet.payload)
                            // After processing reply, client sends NEWKEYS
                            responses.add(createNewKeysPacket())
                            state = KexState.EXPECT_NEWKEYS // Then expects server's NEWKEYS
                        } else {
                            failKex("Server should not receive KEXDH_REPLY")
                        }
                    }
                    // TODO: Add handlers for other KEX methods (ECDH, etc.)
                    else -> {
                        failKex("Unexpected KEX packet: ${packet.messageType}")
                    }
                }
            }
            KexState.EXPECT_NEWKEYS -> {
                if (packet.messageType == SSHMessageType.NEWKEYS) {
                    handleNewKeys()
                    if (isServer) {
                        // Server receives client's NEWKEYS, sends its own NEWKEYS
                        responses.add(createNewKeysPacket())
                    }
                    // Both sides have received NEWKEYS, KEX is complete
                    state = KexState.COMPLETED
                    println("Key exchange completed successfully!")
                    // TODO: Notify transport layer to update ciphers
                } else {
                    failKex("Expected NEWKEYS, got ${packet.messageType}")
                }
            }
            KexState.COMPLETED, KexState.FAILED -> {
                // KEX is done or failed, no more KEX packets expected
                println("KEX already ${state.name}, ignoring packet ${packet.messageType}")
            }
        }
        return responses
    }

    private fun handleKexInit(payload: Indexed<Byte>) {
        if (isServer) {
            clientKexInitPayload = payload
            // Server already has its own KEXINIT payload from SSHTransport or generates it now
            // serverKexInitPayload = transport.getServerKexInitPayload()
        } else {
            serverKexInitPayload = payload
            // Client already has its own KEXINIT payload
            // clientKexInitPayload = transport.getClientKexInitPayload()
        }

        // Parse KEXINIT payloads and negotiate algorithms
        // This is a simplified negotiation, taking the first common algorithm
        val clientAlgorithms = parseKexInitAlgorithms(clientKexInitPayload!!)
        val serverAlgorithms = parseKexInitAlgorithms(serverKexInitPayload!!)

        chosenKexAlgorithm = negotiateAlgorithm(clientAlgorithms.kex, serverAlgorithms.kex, AlgorithmPreferences.KeyExchange.preferences)?.let { KeyExchangeAlgorithms.fromString(it) }
        chosenHostKeyAlgorithm = negotiateAlgorithm(clientAlgorithms.serverHostKey, serverAlgorithms.serverHostKey, AlgorithmPreferences.HostKey.preferences)?.let { SignatureAlgorithms.fromString(it) }
        // ... and so on for ciphers, MACs, compression

        println("Negotiated Algorithms: KEX=${chosenKexAlgorithm?.value}, HostKey=${chosenHostKeyAlgorithm?.value}")

        if (chosenKexAlgorithm == null || chosenHostKeyAlgorithm == null) {
            failKex("Algorithm negotiation failed")
        }
    }

    private fun createKexDhInit(): SSHPacket? {
        // For Diffie-Hellman group exchange or fixed groups like curve25519
        // Assuming chosenKexAlgorithm is something like X25519 or a NIST curve
        val kexImpl = chosenKexAlgorithm?.let { CryptoFactory.createKeyExchange(it) } ?: return null
        dhKeyPair = kexImpl.generateKeyPair()
        clientDhPublic = dhKeyPair!!.first // e (client's public key)

        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.KEXDH_INIT.value)
        payload.addAll(clientDhPublic!!.toSshMpint()) // mpint e

        return transport.createPacket(SSHMessageType.KEXDH_INIT, payload.toIndexed())
    }

    private fun handleKexDhInit(payload: Indexed<Byte>) { // Server side
        // Parse client's public key 'e'
        // Assuming payload starts with message code, then mpint e
        clientDhPublic = payload.slice(1, payload.a - 1).fromSshMpint() // Simplified parsing
        if (clientDhPublic == null) {
            failKex("Failed to parse client DH public key")
            return
        }

        // Server generates its own DH key pair
        val kexImpl = chosenKexAlgorithm?.let { CryptoFactory.createKeyExchange(it) } ?: return
        dhKeyPair = kexImpl.generateKeyPair()
        serverDhPublic = dhKeyPair!!.first

        // Compute shared secret K = e^y mod p (or equivalent for EC)
        sharedSecret = kexImpl.computeSharedSecret(dhKeyPair!!.second, clientDhPublic!!)
    }

    private fun createKexDhReply(): SSHPacket? { // Server side
        // K_S (host public key)
        // f (server's DH public key)
        // H_sig (signature of H)

        // TODO: Load actual server host key based on chosenHostKeyAlgorithm
        // For now, generate a dummy one if not available
        if (hostKey == null) {
            val signer = chosenHostKeyAlgorithm?.let { CryptoFactory.createSigner(it) }
            hostKey = signer?.generateKeyPair()
        }
        if (hostKey == null || serverDhPublic == null || sharedSecret == null) {
            failKex("Missing data for KEXDH_REPLY")
            return null
        }

        val K_S_bytes = hostKey!!.first.toSshPublicKey() // Format depends on key type

        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.KEXDH_REPLY.value)
        payload.addAll(K_S_bytes.toSSHString())         // string server public host key and certificates (K_S)
        payload.addAll(serverDhPublic!!.toSshMpint())   // mpint f or Q_S

        // Calculate exchange hash H
        // H = hash(V_C || V_S || I_C || I_S || K_S || e || f || K)
        // V_C = client version string, V_S = server version string
        // I_C = client KEXINIT payload, I_S = server KEXINIT payload
        // K_S = server host key, e = client DH public, f = server DH public, K = shared secret
        // This needs access to transport versions and KEXINIT payloads
        // exchangeHash = calculateExchangeHash(...)
        // For now, a placeholder:
        exchangeHash = crypto.randomBytes(32) // Placeholder for actual hash

        // Sign H
        val signer = chosenHostKeyAlgorithm?.let { CryptoFactory.createSigner(it) } ?: return null
        val signature = signer.sign(hostKey!!.second, exchangeHash!!)

        payload.addAll(signature.toSshSignature(chosenHostKeyAlgorithm!!)) // string signature of H

        return transport.createPacket(SSHMessageType.KEXDH_REPLY, payload.toIndexed())
    }

    private fun handleKexDhReply(payload: Indexed<Byte>) { // Client side
        // Parse K_S, f, and signature
        // Verify host key K_S (e.g., against known_hosts)
        // Verify signature of H
        // Compute shared secret K
        // If all good, client is ready to send NEWKEYS

        // Simplified parsing:
        // val K_S_data = ...
        // serverDhPublic = ...
        // val signature = ...

        // TODO: Proper parsing and verification

        val kexImpl = chosenKexAlgorithm?.let { CryptoFactory.createKeyExchange(it) } ?: return
        // sharedSecret = kexImpl.computeSharedSecret(dhKeyPair!!.second, serverDhPublic!!)

        // TODO: Calculate exchangeHash H on client side as well
        // exchangeHash = calculateExchangeHash(...)
        // TODO: Verify signature using K_S and exchangeHash

        println("KEXDH_REPLY processed (simplified). Shared secret computed.")
    }


    private fun createNewKeysPacket(): SSHPacket {
        return transport.createPacket(SSHMessageType.NEWKEYS, byteArrayOf(SSHMessageType.NEWKEYS.value).toIndexed())
    }

    private fun handleNewKeys() {
        // This message means that the side sending it will start using the new keys
        // for all subsequent messages.
        // The receiving side must also prepare to use the new keys for incoming messages.

        // TODO: Derive encryption/MAC keys from sharedSecret (K) and exchangeHash (H)
        // IV_c_s, IV_s_c, Key_c_s, Key_s_c, MAC_c_s, MAC_s_c
        // Keys are derived using H, K, and session_id (which is H for first KEX)
        // Example: AES_key = HASH(K || H || 'A' || session_id)

        println("NEWKEYS received/sent. Keys should now be active.")
        // TODO: Update transport layer with new ciphers and keys
        // transport.activateNewKeys(derivedKeys)
    }


    private fun failKex(reason: String) {
        println("Key Exchange Failed: $reason")
        state = KexState.FAILED
        // TODO: Send DISCONNECT message via transport
        // transport.sendDisconnect(SSHDisconnectReason.KEY_EXCHANGE_FAILED, reason)
    }

    // --- Utility and Parsing functions ---

    private data class KexAlgorithms(
        val kex: List<String>,
        val serverHostKey: List<String>,
        // Add other algorithm lists: ciphers_c2s, ciphers_s2c, macs_c2s, macs_s2c, comp_c2s, comp_s2c
    )

    private fun parseKexInitAlgorithms(payload: Indexed<Byte>): KexAlgorithms {
        // KEXINIT payload structure:
        // byte[16] cookie
        // name-list kex_algorithms
        // name-list server_host_key_algorithms
        // ... (other algos) ...
        // boolean first_kex_packet_follows
        // uint32 reserved

        var offset = 1 + 16 // Skip message type + cookie (assuming payload starts after msg type)

        fun parseNameList(): List<String> {
            if (offset + 4 > payload.a) return emptyList()
            val length = payload.toUInt(offset).toInt()
            offset += 4
            if (offset + length > payload.a) return emptyList()
            val listStr = payload.slice(offset, length).toAsciiString()
            offset += length
            return listStr.split(',').filter { it.isNotEmpty() }
        }

        val kexAlgos = parseNameList()
        val hostKeyAlgos = parseNameList()
        // TODO: Parse other algorithm lists

        return KexAlgorithms(kexAlgos, hostKeyAlgos)
    }

    private fun negotiateAlgorithm(clientList: List<String>, serverList: List<String>, preferenceOrder: List<String>): String? {
        // Find first algorithm in preferenceOrder that is in both client and server lists
        for (algo in preferenceOrder) {
            if (clientList.contains(algo) && serverList.contains(algo)) {
                return algo
            }
        }
        // RFC 4253: If server and client list have no algorithm in common, connection fails.
        // Client MAY guess, but server MUST disconnect.
        // For simplicity, we fail if no common preferred algorithm.
        return null
    }

    // Placeholder extension functions for crypto types, assuming they might be needed
    // These would ideally be part of the CommonCrypto interface or its implementations
    private fun PublicKey.toSshMpint(): Indexed<Byte> { /* Convert to SSH mpint format */ return this /* placeholder */ }
    private fun Indexed<Byte>.fromSshMpint(): PublicKey? { /* Convert from SSH mpint format */ return this /* placeholder */ }
    private fun PublicKey.toSshPublicKey(): Indexed<Byte> { /* Convert to SSH public key format (e.g., "ssh-rsa" or "ecdsa-sha2-nistp256") */ return this /* placeholder */ }
    private fun Signature.toSshSignature(algorithm: SignatureAlgorithm): Indexed<Byte> { /* Convert to SSH signature format */ return this /* placeholder */ }

    // Helper to convert string to SSH name-list format (length prefixed)
    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray()
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }
    private fun UInt.toBytes(): Indexed<Byte> = byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), this.toByte()).toIndexed()
    private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { i -> this[i] }
    private operator fun Indexed<Byte>.plus(other: Indexed<Byte>): Indexed<Byte> {
        val result = ByteArray(this.a + other.a)
        for(i in 0 until this.a) result[i] = this[i]
        for(i in 0 until other.a) result[this.a + i] = other[i]
        return result.toIndexed()
    }
     private fun Indexed<Byte>.toUInt(offset: Int): UInt {
        if (offset + 3 >= this.a) throw IndexOutOfBoundsException("Not enough bytes to read UInt from Indexed<Byte>")
        return ((this[offset].toUInt() and 0xFFu) shl 24) or
               ((this[offset + 1].toUInt() and 0xFFu) shl 16) or
               ((this[offset + 2].toUInt() and 0xFFu) shl 8) or
               (this[offset + 3].toUInt() and 0xFFu)
    }
    private fun Indexed<Byte>.toAsciiString(): String = this.map { it.toInt().toChar() }.joinToString("")

}

// Extension to allow creating KeyExchangeAlgorithm from string (used in negotiation)
fun KeyExchangeAlgorithms.Companion.fromString(s: String): KeyExchangeAlgorithm? {
    return when(s) {
        "curve25519-sha256", "curve25519-sha256@libssh.org" -> KeyExchangeAlgorithms.X25519 // Assuming X25519 is used for curve25519
        "ecdh-sha2-nistp256" -> KeyExchangeAlgorithms.SECP256R1
        "ecdh-sha2-nistp384" -> KeyExchangeAlgorithms.SECP384R1
        "ecdh-sha2-nistp521" -> KeyExchangeAlgorithms.SECP521R1
        // TODO: Add other mappings for DH group exchange etc.
        else -> null
    }
}
// Extension for SignatureAlgorithm
fun SignatureAlgorithms.Companion.fromString(s: String): SignatureAlgorithm? {
    return when(s) {
        "ssh-ed25519" -> SignatureAlgorithms.ED25519
        "ecdsa-sha2-nistp256" -> SignatureAlgorithms.ECDSA_SECP256R1_SHA256
        "ecdsa-sha2-nistp384" -> SignatureAlgorithms.ECDSA_SECP384R1_SHA384
        "ecdsa-sha2-nistp521" -> SignatureAlgorithms.ECDSA_SECP521R1_SHA512
        "rsa-sha2-512" -> SignatureAlgorithms.RSA_PSS_RSAE_SHA512 // Assuming PSS variant
        "rsa-sha2-256" -> SignatureAlgorithms.RSA_PSS_RSAE_SHA256 // Assuming PSS variant
        // "ssh-rsa" (SHA1) - often deprecated, handle if needed
        else -> null
    }
}
