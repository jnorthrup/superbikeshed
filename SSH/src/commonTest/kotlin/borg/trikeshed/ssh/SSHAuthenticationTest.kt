package borg.trikeshed.ssh

import borg.trikeshed.crypto.CommonCrypto
import borg.trikeshed.crypto.CryptoFactory
import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SSHAuthenticationTest {

    private fun createMockTransport(isServer: Boolean = true): SSHTransport {
        return SSHTransport(isServer = isServer, crypto = CryptoFactory.getSecureRandom())
    }

    private fun createMockCrypto(): CommonCrypto {
        return CryptoFactory.getSecureRandom()
    }

    @Test
    fun testServiceRequest_UserAuth() {
        val serverTransport = createMockTransport(isServer = true)
        val serverAuth = SSHAuthentication(serverTransport, createMockCrypto(), isServer = true)

        // Client sends SERVICE_REQUEST for "ssh-userauth"
        val serviceRequestPayload = mutableListOf<Byte>()
        serviceRequestPayload.add(SSHMessageType.SERVICE_REQUEST.value)
        serviceRequestPayload.addAll(SSHService.USERAUTH.serviceName.toSSHString())

        val serviceRequestPacket = SSHPacket(
            SSHMessageType.SERVICE_REQUEST, 0u,
            serviceRequestPayload.size.toUInt(), 0u,
            serviceRequestPayload.toIndexed()
        )

        val responses = serverAuth.processPacket(serviceRequestPacket)

        assertEquals(1, responses.size, "Should get one response for SERVICE_REQUEST")
        val serviceAcceptPacket = responses[0]
        // assertEquals(SSHMessageType.SERVICE_ACCEPT, serviceAcceptPacket.messageType) // Assuming processPacket returns SSHPacket

        // If processPacket returns raw bytes to send:
        // Need to parse the raw bytes to check type and payload.
        // For now, we assume the internal state of serverAuth changes correctly.
        // TODO: Add assertion for serverAuth internal state if possible/exposed.
    }

    @Test
    fun testUserAuthRequest_Password_Success() {
        val serverTransport = createMockTransport(isServer = true)
        val serverAuth = SSHAuthentication(serverTransport, createMockCrypto(), isServer = true)

        // Simulate successful SERVICE_REQUEST first to move state
        val serviceRequestPayload = mutableListOf<Byte>()
        serviceRequestPayload.add(SSHMessageType.SERVICE_REQUEST.value)
        serviceRequestPayload.addAll(SSHService.USERAUTH.serviceName.toSSHString())
        serverAuth.processPacket(SSHPacket(SSHMessageType.SERVICE_REQUEST, 0u, 0u,0u, serviceRequestPayload.toIndexed()))


        // Client sends USERAUTH_REQUEST with method "password"
        val username = "testuser"
        val password = "password123"

        val authRequestPayload = mutableListOf<Byte>()
        authRequestPayload.add(SSHMessageType.USERAUTH_REQUEST.value)
        authRequestPayload.addAll(username.toSSHString())
        authRequestPayload.addAll(SSHService.USERAUTH.serviceName.toSSHString()) // Service name again
        authRequestPayload.addAll(SSHAuthMethod.PASSWORD.methodName.toSSHString())
        authRequestPayload.add(0.toByte()) // boolean FALSE (no password change)
        authRequestPayload.addAll(password.toSSHString())

        val userAuthRequestPacket = SSHPacket(
            SSHMessageType.USERAUTH_REQUEST, 1u,
            authRequestPayload.size.toUInt(), 0u,
            authRequestPayload.toIndexed()
        )

        val responses = serverAuth.processPacket(userAuthRequestPacket)
        assertEquals(1, responses.size)
        // TODO: Parse response and check for SSH_MSG_USERAUTH_SUCCESS
        // val successPacket = responses[0]
        // assertEquals(SSHMessageType.USERAUTH_SUCCESS, successPacket.messageType)
    }

    @Test
    fun testUserAuthRequest_Password_Failure() {
        val serverTransport = createMockTransport(isServer = true)
        val serverAuth = SSHAuthentication(serverTransport, createMockCrypto(), isServer = true)
        // Simulate SERVICE_REQUEST
        val srPayload = mutableListOf<Byte>().apply { add(SSHMessageType.SERVICE_REQUEST.value); addAll(SSHService.USERAUTH.serviceName.toSSHString()) }
        serverAuth.processPacket(SSHPacket(SSHMessageType.SERVICE_REQUEST, 0u, 0u,0u, srPayload.toIndexed()))

        val username = "testuser"
        val wrongPassword = "wrongpassword"

        val authRequestPayload = mutableListOf<Byte>()
        authRequestPayload.add(SSHMessageType.USERAUTH_REQUEST.value)
        authRequestPayload.addAll(username.toSSHString())
        authRequestPayload.addAll(SSHService.USERAUTH.serviceName.toSSHString())
        authRequestPayload.addAll(SSHAuthMethod.PASSWORD.methodName.toSSHString())
        authRequestPayload.add(0.toByte()) // FALSE
        authRequestPayload.addAll(wrongPassword.toSSHString())

        val userAuthRequestPacket = SSHPacket(SSHMessageType.USERAUTH_REQUEST, 1u, 0u,0u, authRequestPayload.toIndexed())
        val responses = serverAuth.processPacket(userAuthRequestPacket)

        assertEquals(1, responses.size)
        // TODO: Parse response and check for SSH_MSG_USERAUTH_FAILURE and permitted methods
    }

    @Test
    fun testUserAuthRequest_PublicKey_Query() {
        val serverTransport = createMockTransport(isServer = true)
        val serverAuth = SSHAuthentication(serverTransport, createMockCrypto(), isServer = true)
        // Simulate SERVICE_REQUEST
        val srPayload = mutableListOf<Byte>().apply { add(SSHMessageType.SERVICE_REQUEST.value); addAll(SSHService.USERAUTH.serviceName.toSSHString()) }
        serverAuth.processPacket(SSHPacket(SSHMessageType.SERVICE_REQUEST, 0u, 0u,0u, srPayload.toIndexed()))

        val username = "testuser"
        val pkAlgo = "ssh-ed25519" // Example
        val pkBlob = CryptoFactory.getSecureRandom().randomBytes(32) // Dummy public key blob

        val authRequestPayload = mutableListOf<Byte>()
        authRequestPayload.add(SSHMessageType.USERAUTH_REQUEST.value)
        authRequestPayload.addAll(username.toSSHString())
        authRequestPayload.addAll(SSHService.USERAUTH.serviceName.toSSHString())
        authRequestPayload.addAll(SSHAuthMethod.PUBLICKEY.methodName.toSSHString())
        authRequestPayload.add(0.toByte()) // boolean FALSE (this is a query, no signature)
        authRequestPayload.addAll(pkAlgo.toSSHString())
        authRequestPayload.addAll(pkBlob.toSSHString()) // Key blob itself is SSH string encoded

        val userAuthRequestPacket = SSHPacket(SSHMessageType.USERAUTH_REQUEST, 1u, 0u,0u, authRequestPayload.toIndexed())
        val responses = serverAuth.processPacket(userAuthRequestPacket)

        assertEquals(1, responses.size)
        // TODO: Parse response and check for SSH_MSG_USERAUTH_PK_OK
        // This requires SSHAuthentication.isPublicKeyAcceptable to be true for "testuser"
    }


    // --- Utility functions from SSHAuthentication.kt (or a common test utility) ---
    private fun String.toSSHString(): Indexed<Byte> {
        val bytes = this.encodeToByteArray()
        val lengthBytes = bytes.size.toUInt().toBytes()
        return lengthBytes + bytes.toIndexed()
    }
    private fun Indexed<Byte>.toSSHString(): Indexed<Byte> {
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
}
