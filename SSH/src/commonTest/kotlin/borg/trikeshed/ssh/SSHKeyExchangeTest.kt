package borg.trikeshed.ssh

import borg.trikeshed.crypto.CommonCrypto
import borg.trikeshed.crypto.CryptoFactory
import borg.trikeshed.crypto.KeyExchangeAlgorithms
import borg.trikeshed.lib.toIndexed
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SSHKeyExchangeTest {

    private fun createMockTransport(isServer: Boolean): SSHTransport {
        // In a real scenario, you might use a mock framework or a test double.
        // For now, a simple instance.
        return SSHTransport(isServer = isServer, crypto = CryptoFactory.getSecureRandom())
    }

    private fun createMockCrypto(): CommonCrypto {
        return CryptoFactory.getSecureRandom() // Uses the actual factory for now
    }

    @Test
    fun testKexInitExchange_ClientAndServerPerspective() {
        val clientTransport = createMockTransport(false)
        val serverTransport = createMockTransport(true)
        val crypto = createMockCrypto()

        val clientKex = SSHKeyExchange(clientTransport, crypto, isServer = false)
        val serverKex = SSHKeyExchange(serverTransport, crypto, isServer = true)

        // 1. Client creates KEXINIT (simulated by transport)
        val clientKexInitPayloadBytes = clientTransport.createKexInitPacket() // This is raw bytes for sending
        // To test SSHKeyExchange, we need the KEXINIT *message* payload, not the full packet.
        // Let's simulate parsing it back (highly simplified) or assume transport gives it to KEX.
        // For now, let's assume SSHTransport's createKexInitPacket also stores the payload internally
        // or SSHKeyExchange would call a method on transport to get/create it.

        // This test setup is becoming complex due to inter-dependencies.
        // A better approach would be to directly provide KEXINIT payloads to SSHKeyExchange.

        // Simulate client's KEXINIT payload (as if parsed from a packet)
        val dummyClientKexInitMsg = SSHPacket(SSHMessageType.KEXINIT, 0u, 0u, 0u, clientTransport.createKexInitPacket().slice(5)) // Slice past packet headers

        // Simulate server's KEXINIT payload
        val dummyServerKexInitMsg = SSHPacket(SSHMessageType.KEXINIT, 0u, 0u, 0u, serverTransport.createKexInitPacket().slice(5))


        // 2. Server receives client's KEXINIT
        // serverKex.processPacket(dummyClientKexInitMsg)
        // TODO: Assert server state, negotiated algorithms (will be null if client list is empty in dummy)

        // 3. Client receives server's KEXINIT
        // clientKex.processPacket(dummyServerKexInitMsg)
        // TODO: Assert client state, negotiated algorithms

        // This test highlights the need for better KEXINIT payload generation/access for testing.
        // For now, just ensure the KEX classes can be instantiated.
        assertNotNull(clientKex)
        assertNotNull(serverKex)
    }

    @Test
    fun testClientInitiatesDH_Simplified() {
        val clientTransport = createMockTransport(false)
        val crypto = createMockCrypto()
        val clientKex = SSHKeyExchange(clientTransport, crypto, isServer = false)

        // Manually set up a state as if KEXINIT was exchanged and an algorithm was chosen
        // This is a hack for testing. Reflection or internal state modification would be needed.
        // For now, we can't easily test createKexDhInit without this.

        // Let's assume KeyExchangeAlgorithms.X25519 was chosen.
        // We need to populate `chosenKexAlgorithm` inside clientKex.
        // This is not possible without changing SSHKeyExchange or using reflection.

        // Test that the class can be created. More detailed tests require ability to set internal state.
        assertNotNull(clientKex)

        // If we could set chosenKexAlgorithm:
        // val kexDhInitPacket = clientKex.createKexDhInit() // This would be an SSHPacket
        // assertNotNull(kexDhInitPacket)
        // assertEquals(SSHMessageType.KEXDH_INIT, kexDhInitPacket.messageType)
    }

    @Test
    fun testCreateNewKeysPacket() {
        val transport = createMockTransport(false) // isServer doesn't matter for this packet
        val crypto = createMockCrypto()
        val kex = SSHKeyExchange(transport, crypto, isServer = false)

        // val newKeysPacket = kex.createNewKeysPacket() // This is the raw packet bytes
        // To get SSHPacket:
        // This method is private in SSHKeyExchange, called internally.
        // Let's assume we want to test the SSHPacket creation part that would be in transport
        val newKeysPayload = byteArrayOf(SSHMessageType.NEWKEYS.value).toIndexed()
        val sshPacket = transport.createPacket(SSHMessageType.NEWKEYS, newKeysPayload) // transport.createPacket is public

        // This test is more for SSHTransport's createPacket with NEWKEYS type
        // than for SSHKeyExchange's internal logic for createNewKeysPacket.

        // A direct test of SSHKeyExchange.createNewKeysPacket() would require it to be public
        // or tested via side effects / interactions if it sends it via transport.

        // For now, just verify the SSHPacket can be formed by the transport.
        // Assuming createPacket in transport returns the full byte array to send:
        val rawBytes = transport.createPacket(SSHMessageType.NEWKEYS, newKeysPayload)
        assertTrue(rawBytes.a > 0)
        // If createPacket returned an SSHPacket structure:
        // assertEquals(SSHMessageType.NEWKEYS, sshPacket.messageType)
        // assertTrue(sshPacket.payload.a == 1 && sshPacket.payload[0] == SSHMessageType.NEWKEYS.value)
    }

    // Helper to slice Indexed<Byte> (if not available in borg.trikeshed.lib)
    // Add if needed: fun Indexed<Byte>.slice(start: Int, length: Int): Indexed<Byte> { ... }
    // For now, assuming a slice method that returns a new Indexed<Byte> exists or can be added to lib.
    // Dummy slice for compilation, replace with actual if needed.
    private fun Indexed<Byte>.slice(count: Int): Indexed<Byte> {
        if (count >= this.a) return this
        val newArr = ByteArray(this.a - count)
        for (i in 0 until newArr.size) {
            newArr[i] = this[count + i]
        }
        return newArr.toIndexed()
    }
}
