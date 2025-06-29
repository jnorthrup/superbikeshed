package borg.trikeshed.ssh

import borg.trikeshed.lib.*
import borg.trikeshed.crypto.* // For public key operations

class SSHAuthentication(
    private val transport: SSHTransport,
    private val crypto: CommonCrypto, // For signature verification etc.
    private val isServer: Boolean
) {
    private var authState: AuthState = AuthState.EXPECT_SERVICE_REQUEST
    private var username: String? = null
    private var serviceName: String? = null
    private var availableAuthMethods: List<SSHAuthMethod> = listOf(SSHAuthMethod.PUBLICKEY, SSHAuthMethod.PASSWORD, SSHAuthMethod.NONE) // Server default

    // Store user's public key if provided during publickey auth
    private var userPublicKey: PublicKey? = null

    private enum class AuthState {
        EXPECT_SERVICE_REQUEST, // ssh-userauth
        EXPECT_USERAUTH_REQUEST,
        EXPECT_USERAUTH_METHOD_SPECIFIC, // e.g. PK_OK for publickey
        AUTHENTICATED,
        FAILED
    }

    fun processPacket(packet: SSHPacket): List<SSHPacket> {
        val responses = mutableListOf<SSHPacket>()
        when (authState) {
            AuthState.EXPECT_SERVICE_REQUEST -> {
                if (packet.messageType == SSHMessageType.SERVICE_REQUEST) {
                    handleServiceRequest(packet.payload)
                    if (serviceName == SSHService.USERAUTH.serviceName) {
                        responses.add(createServiceAcceptPacket(SSHService.USERAUTH.serviceName))
                        authState = AuthState.EXPECT_USERAUTH_REQUEST
                    } else {
                        // Disconnect if wrong service requested
                        failAuth("Unsupported service: $serviceName")
                        // responses.add(transport.createDisconnectPacket(...))
                    }
                } else {
                    failAuth("Expected SERVICE_REQUEST, got ${packet.messageType}")
                }
            }
            AuthState.EXPECT_USERAUTH_REQUEST -> {
                if (packet.messageType == SSHMessageType.USERAUTH_REQUEST) {
                    val (user, service, method, methodPayload) = parseUserAuthRequest(packet.payload)
                    // TODO: Validate user, service
                    username = user
                    println("User '$username' trying to authenticate with method '$method'")

                    when (SSHAuthMethod.fromName(method)) {
                        SSHAuthMethod.NONE -> {
                            // 'none' auth is usually for initial query of allowed methods
                            // Or if server allows it (rarely for actual login)
                            if (checkNoneAuth(user)) {
                                responses.add(createAuthSuccessPacket())
                                authState = AuthState.AUTHENTICATED
                            } else {
                                responses.add(createAuthFailurePacket(getPermittedMethods(user), partialSuccess = false))
                            }
                        }
                        SSHAuthMethod.PASSWORD -> {
                            if (checkPasswordAuth(user, methodPayload)) {
                                responses.add(createAuthSuccessPacket())
                                authState = AuthState.AUTHENTICATED
                            } else {
                                responses.add(createAuthFailurePacket(getPermittedMethods(user), partialSuccess = false))
                            }
                        }
                        SSHAuthMethod.PUBLICKEY -> {
                            // Public key auth is a two-step process if signature is not included initially
                            // 1. Client sends USERAUTH_REQUEST with public key (no signature)
                            // 2. Server replies with USERAUTH_PK_OK if key is acceptable
                            // 3. Client sends USERAUTH_REQUEST again with public key AND signature
                            // 4. Server verifies signature

                            val (algo, pkBlob, hasSignature) = parsePublicKeyAuthPayload(methodPayload)
                            if (hasSignature) {
                                if (checkPublicKeySignature(user, algo, pkBlob, methodPayload /* pass full payload for signature part */)) {
                                    responses.add(createAuthSuccessPacket())
                                    authState = AuthState.AUTHENTICATED
                                } else {
                                    responses.add(createAuthFailurePacket(getPermittedMethods(user), partialSuccess = false))
                                }
                            } else {
                                // No signature, this is a query or first step
                                if (isPublicKeyAcceptable(user, algo, pkBlob)) {
                                    userPublicKey = pkBlob // Store for signature check
                                    responses.add(createAuthPkOkPacket(algo, pkBlob))
                                    // No state change, still EXPECT_USERAUTH_REQUEST for the signed one
                                } else {
                                    responses.add(createAuthFailurePacket(getPermittedMethods(user), partialSuccess = false))
                                }
                            }
                        }
                        // TODO: Add other auth methods (hostbased, keyboard-interactive)
                        else -> {
                            println("Unsupported auth method: $method")
                            responses.add(createAuthFailurePacket(getPermittedMethods(user), partialSuccess = false))
                        }
                    }
                } else {
                     failAuth("Expected USERAUTH_REQUEST, got ${packet.messageType}")
                }
            }
            // EXPECT_USERAUTH_METHOD_SPECIFIC might be needed for keyboard-interactive
            AuthState.AUTHENTICATED, AuthState.FAILED -> {
                 println("Authentication already ${authState.name}, ignoring packet ${packet.messageType}")
            }
            else -> { /* Should not happen */ }
        }
        return responses
    }

    private fun handleServiceRequest(payload: Indexed<Byte>) {
        // Payload: string service_name
        serviceName = payload.slice(1, payload.a - 1).parseSshString() // Skip message type byte
        println("Service request for: $serviceName")
    }

    private fun createServiceAcceptPacket(service: String): SSHPacket {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.SERVICE_ACCEPT.value)
        payload.addAll(service.toSSHString())
        return transport.createPacket(SSHMessageType.SERVICE_ACCEPT, payload.toIndexed())
    }

    private data class UserAuthRequestPayload(val user: String, val service: String, val method: String, val methodSpecificData: Indexed<Byte>)
    private fun parseUserAuthRequest(payload: Indexed<Byte>): UserAuthRequestPayload {
        var offset = 1 // Skip message type
        val user = payload.parseSshString(offset) { offset += it }
        val service = payload.parseSshString(offset) { offset += it }
        val method = payload.parseSshString(offset) { offset += it }
        val methodSpecificData = payload.slice(offset, payload.a - offset)
        return UserAuthRequestPayload(user, service, method, methodSpecificData)
    }

    private fun checkNoneAuth(username: String?): Boolean {
        // Implement logic: e.g., allow 'none' for a specific user or if no auth is configured
        return false // Typically 'none' doesn't grant access unless specifically configured
    }

    private fun checkPasswordAuth(username: String?, passwordPayload: Indexed<Byte>): Boolean {
        // passwordPayload: boolean FALSE, string password
        var offset = 0
        val changePasswd = passwordPayload[offset++] == 1.toByte() // Should be FALSE for initial auth
        val password = passwordPayload.parseSshString(offset) { offset += it }

        // TODO: Implement actual password verification against a user database or system
        println("Checking password for $username: '$password' (changePasswd=$changePasswd)")
        return username == "testuser" && password == "password123" // Dummy check
    }

    private data class PublicKeyAuthData(val algoName: String, val pkBlob: Indexed<Byte>, val hasSignature: Boolean)
    private fun parsePublicKeyAuthPayload(payload: Indexed<Byte>): PublicKeyAuthData {
        // Payload: boolean has_signature, string public_key_algorithm_name, string public_key_blob
        // If has_signature is TRUE, then also: string signature
        var offset = 0
        val hasSig = payload[offset++] == 1.toByte()
        val algo = payload.parseSshString(offset) { offset += it }
        val pkBlob = payload.parseSshString(offset) { offset += it }.fromSSHStringPayload() // Assuming it's length-prefixed data

        // Signature parsing would be here if hasSig is true
        // val signature = if (hasSig) payload.parseSshString(offset) { offset += it } else null

        return PublicKeyAuthData(algo, pkBlob, hasSig)
    }

    private fun isPublicKeyAcceptable(username: String?, algoName: String, pkBlob: Indexed<Byte>): Boolean {
        // TODO: Check if this public key is authorized for the user
        // (e.g., by checking ~/.ssh/authorized_keys)
        // For now, accept any key for a test user
        println("Checking if public key (algo=$algoName) is acceptable for $username")
        return username == "testuser"
    }

    private fun checkPublicKeySignature(username: String?, algoName: String, pkBlob: Indexed<Byte>, fullAuthRequestPayload: Indexed<Byte>): Boolean {
        // This is complex. The signature is over:
        // session_id || byte SSH_MSG_USERAUTH_REQUEST || string username || string service_name ||
        // string "publickey" || boolean TRUE || string public_key_algorithm_name || string public_key_blob

        // TODO: Reconstruct the signed data correctly
        // TODO: Parse the signature from fullAuthRequestPayload
        // TODO: Get the public key (pkBlob) and verify the signature

        // val signer = SignatureAlgorithms.fromString(algoName)?.let { CryptoFactory.createSigner(it) }
        // val signedData = ... construct ...
        // val signature = ... parse from fullAuthRequestPayload ...
        // return signer?.verify(pkBlob, signedData, signature) ?: false

        println("Checking public key signature for $username (algo=$algoName) - SIMPLIFIED, ALWAYS FAILS FOR NOW")
        return false // Placeholder
    }


    private fun createAuthSuccessPacket(): SSHPacket {
        val payload = byteArrayOf(SSHMessageType.USERAUTH_SUCCESS.value).toIndexed()
        return transport.createPacket(SSHMessageType.USERAUTH_SUCCESS, payload)
    }

    private fun createAuthFailurePacket(methodsCanContinue: List<SSHAuthMethod>, partialSuccess: Boolean): SSHPacket {
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.USERAUTH_FAILURE.value)

        val methodNames = methodsCanContinue.joinToString(",") { it.methodName }
        payload.addAll(methodNames.toSSHString())
        payload.add(if (partialSuccess) 1.toByte() else 0.toByte())

        return transport.createPacket(SSHMessageType.USERAUTH_FAILURE, payload.toIndexed())
    }

    private fun createAuthPkOkPacket(algoName: String, pkBlob: Indexed<Byte>): SSHPacket {
        // This is sent by server if client sends USERAUTH_REQUEST with a public key (no signature)
        // and the key is acceptable for authentication.
        // Client should then resend USERAUTH_REQUEST with a signature.
        val payload = mutableListOf<Byte>()
        payload.add(SSHMessageType.USERAUTH_PK_OK.value)
        payload.addAll(algoName.toSSHString())
        payload.addAll(pkBlob.toSSHString()) // The key blob itself needs to be SSH string encoded

        return transport.createPacket(SSHMessageType.USERAUTH_PK_OK, payload.toIndexed())
    }

    private fun getPermittedMethods(username: String?): List<SSHAuthMethod> {
        // TODO: Could be user-specific
        return availableAuthMethods.filterNot { it == SSHAuthMethod.NONE } // 'none' is not advertised
    }

    private fun failAuth(reason: String) {
        println("Authentication Failed: $reason")
        authState = AuthState.FAILED
        // TODO: Send DISCONNECT message via transport if appropriate
    }

    // --- Utility functions ---
    // These should ideally be in a separate utility class or file
    private fun Indexed<Byte>.parseSshString(startOffset: Int, updateOffset: (Int) -> Unit): String {
        if (startOffset + 4 > this.a) return ""
        val length = this.toUInt(startOffset).toInt()
        val dataStart = startOffset + 4
        if (dataStart + length > this.a) return ""
        val strBytes = this.slice(dataStart, length)
        updateOffset(dataStart + length - startOffset) // Pass how many bytes were consumed in total from startOffset
        return strBytes.toAsciiString() // Assuming UTF-8, but using ASCII for simplicity
    }

    private fun Indexed<Byte>.fromSSHStringPayload(): Indexed<Byte> {
        // Assumes 'this' is already the data part of an SSH string (without the length prefix)
        return this
    }

    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray() // UTF-8
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }

    private fun Indexed<Byte>.toSSHString(): Indexed<Byte> {
        // If 'this' is already the data part of an SSH string (e.g. a key blob)
        // then it needs to be wrapped with its own length for packet inclusion.
        val lengthBytes = this.a.toUInt().toBytes()
        return lengthBytes + this
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
