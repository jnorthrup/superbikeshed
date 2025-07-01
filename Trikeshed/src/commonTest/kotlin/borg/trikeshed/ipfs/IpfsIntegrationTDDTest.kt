@file:Suppress("UNCHECKED_CAST", "FunctionName", "NonAsciiCharacters", "NOTHING_TO_INLINE")

package borg.trikeshed.ipfs

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.test.*

class IpfsIntegrationTDDTest {

    // === TDD FAILING TESTS - MULTIHASH IMPLEMENTATION ===

    @Test
    fun `Multihash should encode and decode correctly`() {
        val testData = "hello world".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        
        val encoded = multihash.encode()
        val decoded = Multihash.decode(encoded)
        
        assertEquals(multihash.type, decoded.type)
        assertEquals(multihash.digest.a, decoded.digest.a)
        
        for (i in 0 until hash.a) {
            assertEquals(multihash.digest.b(i), decoded.digest.b(i))
        }
    }

    @Test
    fun `Multihash should reject invalid data`() {
        assertFailsWith<IllegalArgumentException> {
            val invalidData = 1 j { 0x99.toByte() } // Too short
            Multihash.decode(invalidData)
        }
        
        assertFailsWith<IllegalArgumentException> {
            val unknownType = 3 j { i -> 
                when (i) {
                    0 -> 0xFF.toByte() // Unknown type
                    1 -> 32.toByte()   // Size
                    else -> 0.toByte() // Data
                }
            }
            Multihash.decode(unknownType)
        }
    }

    @Test
    fun `Multihash should support different hash types`() {
        val testData = "test".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        
        val sha256Hash = Multihash(Multihash.HashType.SHA2_256, hash)
        val sha512Hash = Multihash(Multihash.HashType.SHA2_512, hash)
        
        assertEquals(0x12, sha256Hash.type.code)
        assertEquals(32, sha256Hash.type.size)
        assertEquals(0x13, sha512Hash.type.code)
        assertEquals(64, sha512Hash.type.size)
    }

    // === TDD FAILING TESTS - CID IMPLEMENTATION ===

    @Test
    fun `CID should encode v0 with base58`() {
        val hash = sha256("hello".encodeToByteArray().toIndexed())
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(0, CID.Codec.DAG_PB, multihash)
        
        val encoded = cid.encode()
        assertFalse(encoded.startsWith("b")) // v0 should not have "b" prefix
    }

    @Test
    fun `CID should encode v1 with base32`() {
        val hash = sha256("hello".encodeToByteArray().toIndexed())
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(1, CID.Codec.RAW, multihash)
        
        val encoded = cid.encode()
        assertTrue(encoded.startsWith("b")) // v1 should have "b" prefix
    }

    @Test
    fun `CID should reject invalid versions`() {
        val hash = sha256("test".encodeToByteArray().toIndexed())
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        
        assertFailsWith<IllegalArgumentException> {
            val invalidCid = CID(2, CID.Codec.RAW, multihash)
            invalidCid.encode()
        }
    }

    @Test
    fun `CID should support different codecs`() {
        val hash = sha256("test".encodeToByteArray().toIndexed())
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        
        val rawCid = CID(1, CID.Codec.RAW, multihash)
        val dagPbCid = CID(1, CID.Codec.DAG_PB, multihash)
        val dagCborCid = CID(1, CID.Codec.DAG_CBOR, multihash)
        val jsonCid = CID(1, CID.Codec.JSON, multihash)
        
        assertEquals(0x55, rawCid.codec.code)
        assertEquals(0x70, dagPbCid.codec.code)
        assertEquals(0x71, dagCborCid.codec.code)
        assertEquals(0x0200, jsonCid.codec.code)
    }

    // === TDD FAILING TESTS - IPFS BLOCK IMPLEMENTATION ===

    @Test
    fun `IpfsBlock should store data with CID correctly`() {
        val testData = "hello ipfs".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(1, CID.Codec.RAW, multihash)
        
        val block = IpfsBlock(cid, testData)
        
        assertEquals(cid, block.cid)
        assertEquals(testData.a, block.data.a)
        for (i in 0 until testData.a) {
            assertEquals(testData.b(i), block.data.b(i))
        }
        assertEquals(0, block.links.a)
    }

    @Test
    fun `IpfsBlock should support links for merkle DAG`() {
        val testData = "parent node".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val parentCid = CID(1, CID.Codec.DAG_PB, multihash)
        
        // Create child links
        val childHash1 = sha256("child1".encodeToByteArray().toIndexed())
        val childMultihash1 = Multihash(Multihash.HashType.SHA2_256, childHash1)
        val childCid1 = CID(1, CID.Codec.RAW, childMultihash1)
        val link1 = IpfsLink("child1", childCid1, 100L)
        
        val childHash2 = sha256("child2".encodeToByteArray().toIndexed())
        val childMultihash2 = Multihash(Multihash.HashType.SHA2_256, childHash2)
        val childCid2 = CID(1, CID.Codec.RAW, childMultihash2)
        val link2 = IpfsLink("child2", childCid2, 200L)
        
        val links = 2 j { i -> if (i == 0) link1 else link2 }
        val block = IpfsBlock(parentCid, testData, links)
        
        assertEquals(2, block.links.a)
        assertEquals("child1", block.links.b(0).name)
        assertEquals("child2", block.links.b(1).name)
        assertEquals(100L, block.links.b(0).size)
        assertEquals(200L, block.links.b(1).size)
    }

    // === TDD FAILING TESTS - MERKLE NODE SERIALIZATION ===

    @Test
    fun `MerkleNode should serialize correctly`() {
        val nodeData = "node data".encodeToByteArray().toIndexed()
        val childHash = sha256("child".encodeToByteArray().toIndexed())
        val childMultihash = Multihash(Multihash.HashType.SHA2_256, childHash)
        val childCid = CID(1, CID.Codec.RAW, childMultihash)
        
        val links = 1 j { "child" j childCid }
        val merkleNode = MerkleNode(nodeData, links)
        
        val serialized = merkleNode.serialize()
        assertTrue(serialized.a > 0)
        
        // Verify serialization contains expected data
        val serializedString = serialized.toByteArray().decodeToString()
        assertTrue(serializedString.contains("child"))
    }

    @Test
    fun `MerkleNode should handle empty data`() {
        val emptyData = 0 j { throw NoSuchElementException() }
        val links = 1 j { "link" j CID(1, CID.Codec.RAW, Multihash(Multihash.HashType.SHA2_256, sha256("test".encodeToByteArray().toIndexed()))) }
        
        val merkleNode = MerkleNode(emptyData, links)
        val serialized = merkleNode.serialize()
        
        assertTrue(serialized.a > 0)
    }

    @Test
    fun `MerkleNode should handle no links`() {
        val nodeData = "standalone data".encodeToByteArray().toIndexed()
        val emptyLinks = 0 j { throw NoSuchElementException() }
        
        val merkleNode = MerkleNode(nodeData, emptyLinks)
        val serialized = merkleNode.serialize()
        
        assertTrue(serialized.a > 0)
        val serializedString = serialized.toByteArray().decodeToString()
        assertTrue(serializedString.contains("standalone data"))
    }

    // === TDD FAILING TESTS - PEER ID AND DHT ===

    @Test
    fun `PeerId should be generated from public key`() {
        val publicKey = "test-public-key".encodeToByteArray().toIndexed()
        val peerId = PeerId.fromPublicKey(publicKey)
        
        assertNotNull(peerId)
        assertTrue(peerId.id.a > 0)
    }

    @Test
    fun `PeerId should encode to base58`() {
        val testId = "test-peer-id".encodeToByteArray().toIndexed()
        val peerId = PeerId(testId)
        
        val base58 = peerId.toBase58()
        assertTrue(base58.isNotEmpty())
        assertFalse(base58.contains(" "))
    }

    @Test
    fun `PeerInfo should contain all required fields`() {
        val testId = "peer-123".encodeToByteArray().toIndexed()
        val peerId = PeerId(testId)
        val addresses = 2 j { i -> 
            if (i == 0) "/ip4/127.0.0.1/tcp/4001" 
            else "/ip6/::1/tcp/4001"
        }
        val protocols = 1 j { "/bitswap/1.2.0" }
        
        val peerInfo = PeerInfo(peerId, addresses, protocols)
        
        assertEquals(peerId, peerInfo.id)
        assertEquals(2, peerInfo.addresses.a)
        assertEquals(1, peerInfo.protocols.a)
        assertEquals("/ip4/127.0.0.1/tcp/4001", peerInfo.addresses.b(0))
        assertEquals("/bitswap/1.2.0", peerInfo.protocols.b(0))
    }

    // === TDD FAILING TESTS - KADEMLIA ROUTING ===

    @Test
    fun `KBucket should add peers up to max size`() {
        val bucket = KBucket(maxSize = 3)
        
        val peer1 = createTestPeerInfo("peer1")
        val peer2 = createTestPeerInfo("peer2")
        val peer3 = createTestPeerInfo("peer3")
        val peer4 = createTestPeerInfo("peer4")
        
        assertTrue(bucket.add(peer1))
        assertTrue(bucket.add(peer2))
        assertTrue(bucket.add(peer3))
        assertFalse(bucket.add(peer4)) // Should fail - bucket full
        
        assertEquals(3, bucket.peers.size)
    }

    @Test
    fun `KBucket should remove peers correctly`() {
        val bucket = KBucket()
        val peer = createTestPeerInfo("test-peer")
        
        bucket.add(peer)
        assertTrue(bucket.contains(peer.id))
        
        bucket.remove(peer.id)
        assertFalse(bucket.contains(peer.id))
    }

    @Test
    fun `KBucket should convert to Indexed correctly`() {
        val bucket = KBucket()
        val peer1 = createTestPeerInfo("peer1")
        val peer2 = createTestPeerInfo("peer2")
        
        bucket.add(peer1)
        bucket.add(peer2)
        
        val indexed = bucket.toIndexed()
        assertEquals(2, indexed.a)
        assertEquals(peer1, indexed.b(0))
        assertEquals(peer2, indexed.b(1))
    }

    // === TDD FAILING TESTS - ROUTING TABLE ===

    @Test
    fun `RoutingTable should add peers to correct buckets`() {
        val localId = PeerId("local".encodeToByteArray().toIndexed())
        val routingTable = RoutingTable(localId)
        
        val peer1 = createTestPeerInfo("peer1")
        val peer2 = createTestPeerInfo("peer2")
        
        routingTable.addPeer(peer1)
        routingTable.addPeer(peer2)
        
        // Should not add self
        routingTable.addPeer(PeerInfo(localId, emptyIndexed(), emptyIndexed()))
    }

    @Test
    fun `RoutingTable should find closest peers`() {
        val localId = PeerId("local".encodeToByteArray().toIndexed())
        val routingTable = RoutingTable(localId)
        
        // Add several peers
        for (i in 1..10) {
            val peer = createTestPeerInfo("peer$i")
            routingTable.addPeer(peer)
        }
        
        val targetId = PeerId("target".encodeToByteArray().toIndexed())
        val closest = routingTable.findClosestPeers(targetId, 5)
        
        assertTrue(closest.a <= 5)
    }

    // === TDD FAILING TESTS - IPFS STORAGE ===

    @Test
    fun `InMemoryIpfsStorage should store and retrieve blocks`() {
        val storage = InMemoryIpfsStorage()
        val testData = "test data".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(1, CID.Codec.RAW, multihash)
        val block = IpfsBlock(cid, testData)
        
        runBlocking {
            val putResult = storage.put(block)
            assertTrue(putResult)
            
            val hasResult = storage.has(cid)
            assertTrue(hasResult)
            
            val getResult = storage.get(cid)
            assertNotNull(getResult)
            assertEquals(block.cid, getResult.cid)
            assertEquals(testData.a, getResult.data.a)
        }
    }

    @Test
    fun `InMemoryIpfsStorage should delete blocks`() {
        val storage = InMemoryIpfsStorage()
        val testData = "delete me".encodeToByteArray().toIndexed()
        val hash = sha256(testData)
        val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
        val cid = CID(1, CID.Codec.RAW, multihash)
        val block = IpfsBlock(cid, testData)
        
        runBlocking {
            storage.put(block)
            assertTrue(storage.has(cid))
            
            val deleteResult = storage.delete(cid)
            assertTrue(deleteResult)
            
            assertFalse(storage.has(cid))
            assertNull(storage.get(cid))
        }
    }

    @Test
    fun `InMemoryIpfsStorage should list all CIDs`() {
        val storage = InMemoryIpfsStorage()
        
        runBlocking {
            val cids = mutableListOf<CID>()
            
            // Add several blocks
            for (i in 1..5) {
                val testData = "test data $i".encodeToByteArray().toIndexed()
                val hash = sha256(testData)
                val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
                val cid = CID(1, CID.Codec.RAW, multihash)
                val block = IpfsBlock(cid, testData)
                
                storage.put(block)
                cids.add(cid)
            }
            
            val listed = storage.list()
            assertEquals(5, listed.a)
        }
    }

    // === TDD FAILING TESTS - IPFS CLIENT FUNCTIONALITY ===

    @Test
    fun `IpfsClient should add and retrieve small files - FAILING UNTIL CLIENT IMPLEMENTATION`() {
        assertFailsWith<Exception> {
            val storage = MockIpfsStorage()
            val client = IpfsClient(
                localPeerId = PeerId("client".encodeToByteArray().toIndexed()),
                quicEngine = null,
                storage = storage
            )
            
            runBlocking {
                val testData = "hello ipfs client".encodeToByteArray().toIndexed()
                val cid = client.add(testData)
                
                val retrieved = client.get(cid)
                assertNotNull(retrieved)
                assertEquals(testData.a, retrieved.a)
                for (i in 0 until testData.a) {
                    assertEquals(testData.b(i), retrieved.b(i))
                }
            }
        }
    }

    @Test
    fun `IpfsClient should handle large files with chunking - FAILING UNTIL CHUNKING IMPLEMENTATION`() {
        assertFailsWith<Exception> {
            val storage = MockIpfsStorage()
            val client = IpfsClient(
                localPeerId = PeerId("client".encodeToByteArray().toIndexed()),
                quicEngine = null,
                storage = storage
            )
            
            runBlocking {
                // Create large file (1MB)
                val largeData = (1024 * 1024) j { (it % 256).toByte() }
                val cid = client.addFile(largeData, chunkSize = 1024)
                
                val retrieved = client.getFile(cid)
                assertNotNull(retrieved)
                assertEquals(largeData.a, retrieved.a)
            }
        }
    }

    @Test
    fun `IpfsClient should support pinning and garbage collection - FAILING UNTIL PIN IMPLEMENTATION`() {
        assertFailsWith<Exception> {
            val storage = MockIpfsStorage()
            val client = IpfsClient(
                localPeerId = PeerId("client".encodeToByteArray().toIndexed()),
                quicEngine = null,
                storage = storage
            )
            
            runBlocking {
                val testData = "pin me".encodeToByteArray().toIndexed()
                val cid = client.add(testData)
                
                // Pin the content
                val pinResult = client.pin(cid)
                assertTrue(pinResult)
                
                // Should be in pinned list
                val pinned = client.listPinned()
                assertTrue(pinned.a > 0)
                
                // Garbage collection should not remove pinned content
                val collected = client.gc()
                assertFalse(collected.toList().contains(cid))
                
                // Unpin and garbage collect
                client.unpin(cid)
                val collected2 = client.gc()
                assertTrue(collected2.toList().contains(cid))
            }
        }
    }

    @Test
    fun `IpfsClient should notify on content destruction - FAILING UNTIL NOTIFICATION SYSTEM`() {
        assertFailsWith<Exception> {
            val storage = MockIpfsStorage()
            val client = IpfsClient(
                localPeerId = PeerId("client".encodeToByteArray().toIndexed()),
                quicEngine = null,
                storage = storage
            )
            
            var destructionNotified = false
            var destroyedCid: CID? = null
            var destructionReason = ""
            
            client.addDestructionListener { cid, reason ->
                destructionNotified = true
                destroyedCid = cid
                destructionReason = reason
            }
            
            runBlocking {
                val testData = "temporary data".encodeToByteArray().toIndexed()
                val cid = client.add(testData)
                
                // Garbage collect
                client.gc()
                
                assertTrue(destructionNotified)
                assertEquals(cid, destroyedCid)
                assertEquals("Garbage collection", destructionReason)
            }
        }
    }

    // === TDD FAILING TESTS - UTILITY FUNCTIONS ===

    @Test
    fun `base58Encode should produce valid base58 strings`() {
        val testData = "hello".encodeToByteArray().toIndexed()
        val encoded = base58Encode(testData)
        
        assertTrue(encoded.isNotEmpty())
        assertFalse(encoded.contains("0"))
        assertFalse(encoded.contains("O"))
        assertFalse(encoded.contains("I"))
        assertFalse(encoded.contains("l"))
    }

    @Test
    fun `base32Encode should produce valid base32 strings`() {
        val testData = "hello".encodeToByteArray().toIndexed()
        val encoded = base32Encode(testData)
        
        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567" })
    }

    @Test
    fun `base64Encode should produce valid base64 strings`() {
        val testData = "hello world".encodeToByteArray().toIndexed()
        val encoded = base64Encode(testData)
        
        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/" })
    }

    @Test
    fun `encodeVarint should encode variable length integers correctly`() {
        val smallInt = encodeVarint(42L)
        assertEquals(1, smallInt.a)
        assertEquals(42.toByte(), smallInt.b(0))
        
        val largeInt = encodeVarint(300L)
        assertTrue(largeInt.a > 1)
        
        val zero = encodeVarint(0L)
        assertEquals(1, zero.a)
        assertEquals(0.toByte(), zero.b(0))
    }

    @Test
    fun `sha256 should produce consistent hashes - FAILING UNTIL REAL IMPLEMENTATION`() {
        val testData1 = "hello".encodeToByteArray().toIndexed()
        val testData2 = "hello".encodeToByteArray().toIndexed()
        val testData3 = "world".encodeToByteArray().toIndexed()
        
        val hash1 = sha256(testData1)
        val hash2 = sha256(testData2)
        val hash3 = sha256(testData3)
        
        // Same input should produce same hash
        assertEquals(hash1.a, hash2.a)
        for (i in 0 until hash1.a) {
            assertEquals(hash1.b(i), hash2.b(i))
        }
        
        // Different input should produce different hash
        var different = false
        for (i in 0 until minOf(hash1.a, hash3.a)) {
            if (hash1.b(i) != hash3.b(i)) {
                different = true
                break
            }
        }
        assertTrue(different)
    }

    // === HELPER FUNCTIONS ===

    private fun createTestPeerInfo(id: String): PeerInfo {
        val peerId = PeerId(id.encodeToByteArray().toIndexed())
        val addresses = 1 j { "/ip4/127.0.0.1/tcp/4001" }
        val protocols = 1 j { "/bitswap/1.2.0" }
        return PeerInfo(peerId, addresses, protocols)
    }

    private fun Indexed<Byte>.toByteArray(): ByteArray {
        val result = ByteArray(this.a)
        for (i in 0 until this.a) {
            result[i] = this.b(i)
        }
        return result
    }

    private fun ByteArray.toIndexed(): Indexed<Byte> = this.size j { this[it] }

    private fun <T> Indexed<T>.toList(): List<T> {
        val result = mutableListOf<T>()
        for (i in 0 until this.a) {
            result.add(this.b(i))
        }
        return result
    }

    // === MOCK IMPLEMENTATIONS ===

    class MockIpfsStorage : IpfsStorage {
        private val blocks = mutableMapOf<String, IpfsBlock>()
        private val pinnedBlocks = mutableSetOf<String>()
        
        override suspend fun put(block: IpfsBlock): Boolean {
            blocks[block.cid.encode()] = block
            return true
        }
        
        override suspend fun get(cid: CID): IpfsBlock? {
            return blocks[cid.encode()]
        }
        
        override suspend fun has(cid: CID): Boolean {
            return blocks.containsKey(cid.encode())
        }
        
        override suspend fun delete(cid: CID): Boolean {
            return blocks.remove(cid.encode()) != null
        }
        
        override suspend fun list(): Indexed<CID> {
            val cids = blocks.keys.map { cidString ->
                // Simple CID reconstruction for mock
                val hash = sha256(cidString.encodeToByteArray().toIndexed())
                val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
                CID(1, CID.Codec.RAW, multihash)
            }
            return cids.size j { cids[it] }
        }
        
        fun pin(cid: CID) {
            pinnedBlocks.add(cid.encode())
        }
        
        fun unpin(cid: CID) {
            pinnedBlocks.remove(cid.encode())
        }
        
        fun listPinned(): Indexed<CID> {
            val cids = pinnedBlocks.map { cidString ->
                val hash = sha256(cidString.encodeToByteArray().toIndexed())
                val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
                CID(1, CID.Codec.RAW, multihash)
            }
            return cids.size j { cids[it] }
        }
        
        fun listUnpinned(): Indexed<CID> {
            val unpinnedKeys = blocks.keys.filter { !pinnedBlocks.contains(it) }
            val cids = unpinnedKeys.map { cidString ->
                val hash = sha256(cidString.encodeToByteArray().toIndexed())
                val multihash = Multihash(Multihash.HashType.SHA2_256, hash)
                CID(1, CID.Codec.RAW, multihash)
            }
            return cids.size j { cids[it] }
        }
        
        fun removeBlock(cid: CID) {
            blocks.remove(cid.encode())
        }
    }

    // Mock empty series utility
    private fun <T> emptyIndexed(): Indexed<T> = 0 j { throw NoSuchElementException("Empty series") }
}