package borg.trikeshed.ipfs

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.UVarint
import borg.trikeshed.lib.emptyIndexed
import borg.trikeshed.lib.plus
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.multiformats.ContentType
import borg.trikeshed.multiformats.HashType
import borg.trikeshed.multiformats.base.Base16
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class IpfsTypesTests {

    private fun assertIndexedEquals(expected: Indexed<Byte>, actual: Indexed<Byte>, message: String? = null) {
        val readableMessage = message ?: "Expected ${Base16.encode(expected)}, got ${Base16.encode(actual)}"
        assertTrue(expected.size == actual.size && (0 until expected.size).all { expected[it] == actual[it] }, readableMessage)
    }

    @Test
    fun testMultihashCreationAndToBytes() {
        val digestHex = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824" // "hello" sha256
        val digest = Base16.decode(digestHex)
        val multihash = Multihash(HashType.SHA2_256, digest)

        assertEquals(HashType.SHA2_256, multihash.type)
        assertIndexedEquals(digest, multihash.digest)

        // Expected bytes: 0x12 (sha2-256 code) + 0x20 (length 32) + digest
        val expectedBytes = Base16.decode("1220" + digestHex)
        assertIndexedEquals(expectedBytes, multihash.toBytes())
    }

    @Test
    fun testMultihashFromBytes() {
        val digestHex = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val multihashBytes = Base16.decode("1220" + digestHex)

        val multihash = Multihash.fromBytes(multihashBytes)
        assertEquals(HashType.SHA2_256, multihash.type)
        assertEquals(32, multihash.digest.size)
        assertIndexedEquals(Base16.decode(digestHex), multihash.digest)
    }

    @Test
    fun testMultihashInvalidDigestSizeRequire() {
         val digestTooShort = Base16.decode("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b98") // missing 1 byte (2 chars)
         assertFailsWith<IllegalArgumentException> {
             Multihash(HashType.SHA2_256, digestTooShort)
         }
    }

    @Test
    fun testCidV1CreationAndToBytes() {
        val digest = HashUtils.sha256("hello".encodeToByteArray().toIndexed()) // Use actual hash for consistency
        val multihash = Multihash(HashType.SHA2_256, digest)
        val cid = CID.v1(ContentType.RAW, multihash)

        assertEquals(1, cid.version)
        assertEquals(ContentType.RAW, cid.codec)
        assertEquals(multihash, cid.multihash)

        // Expected: 0x01 (version) + 0x55 (RAW codec uvarint) + multihash.toBytes()
        val versionByte = byteArrayOf(1).toIndexed()
        val codecVarint = UVarint.encode(ContentType.RAW.code.toLong())
        val expectedBytes = versionByte + codecVarint + multihash.toBytes()

        assertIndexedEquals(expectedBytes, cid.toBytes())
    }

    @Test
    fun testCidV0CreationAndToBytes() {
        // CIDv0 is dag-pb, sha2-256, 32-byte digest
        val digestHex = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val digest = Base16.decode(digestHex)

        val cid = CID.v0(digest)
        assertEquals(0, cid.version)
        assertEquals(ContentType.DAG_PB, cid.codec)
        assertEquals(HashType.SHA2_256, cid.multihash.type)
        assertIndexedEquals(digest, cid.multihash.digest)

        // CIDv0 toBytes is just its multihash.toBytes()
        val expectedMultihashBytes = Base16.decode("1220" + digestHex)
        assertIndexedEquals(expectedMultihashBytes, cid.toBytes())
    }

    @Test
    fun testCidToStringPlaceholders() {
        // This test just confirms the current placeholder toString() format
        // It will need to be updated when proper base encoding (Base58btc, Base32) is added for CIDs
        val digest = HashUtils.sha256("data".encodeToByteArray().toIndexed())
        val multihash = Multihash(HashType.SHA2_256, digest)

        val cidV1 = CID.v1(ContentType.RAW, multihash)
        // Current v1 placeholder: "v1b16-" + Base16.encode(cidV1.toBytes())
        val expectedV1String = "v1b16-" + Base16.encode(cidV1.toBytes())
        assertEquals(expectedV1String, cidV1.toString())

        val cidV0 = CID.v0(digest)
        // Current v0 placeholder: "Qm" + Base16.encode(digest)
        val expectedV0String = "Qm" + Base16.encode(digest)
        assertEquals(expectedV0String, cidV0.toString())
    }


    @Test
    fun testIpfsClientAddGeneratesCorrectCid() = runBlocking {
        val data = "test data".encodeToByteArray().toIndexed()
        val storage = IpfsStorage() // Simple in-memory storage
        val client = IpfsClient(PeerId(emptyIndexed()), null, storage, IpfsConfig())

        val cid = client.add(data)

        // Verify CID components
        assertEquals(1, cid.version, "CID version should be 1")
        assertEquals(ContentType.RAW, cid.codec, "CID codec should be RAW")
        assertEquals(HashType.SHA2_256, cid.multihash.type, "Multihash type should be SHA2_256")

        // Verify digest
        val expectedDigest = HashUtils.sha256(data)
        assertIndexedEquals(expectedDigest, cid.multihash.digest, "Multihash digest mismatch")

        // Check if block was stored
        val storedBlock = storage.getBlock(cid)
        assertNotNull(storedBlock, "Block should be stored")
        assertEquals(cid, storedBlock.cid)
        assertIndexedEquals(data, storedBlock.data)
    }
}
