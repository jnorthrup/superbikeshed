package tests

import borg.trikeshed.lib.*
import borg.trikeshed.ipfs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * TDD Tests for IPFS Gateway Integration
 * 
 * Tests the IPFS gateway functionality:
 * - Content storage and retrieval
 * - CID generation and parsing
 * - Gateway mapping between torrents and IPFS
 * - HTTP API endpoints
 */
class IpfsGatewayTddTest {

    @Test
    fun `test IPFS client initialization`() = runTest {
        // Given: IPFS client parameters
        val peerId = \1 j { \2: Int -> i.toByte() })
        val storage = InMemoryIpfsStorage()
        val config = IpfsConfig()

        // When: Creating IPFS client
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage,
            config = config
        )

        // Then: Client should be properly initialized
        assertEquals(peerId, client.localPeerId)
        assertEquals(storage, client.storage)
        assertEquals(config, client.config)
    }

    @Test
    fun `test content addition to IPFS`() = runTest {
        // Given: An IPFS client and test data
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val testData = \1 j { \2: Int -> (i % 256).toByte() }

        // When: Adding content to IPFS
        val cid = client.add(testData)

        // Then: Should get a valid CID
        assertNotNull(cid)
        assertTrue(cid.version >= 0)
        assertNotNull(cid.multihash)
        assertTrue(cid.multihash.digest.component1() > 0)
    }

    @Test
    fun `test content retrieval from IPFS`() = runTest {
        // Given: An IPFS client with stored content
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val originalData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = client.add(originalData)

        // When: Retrieving content from IPFS
        val retrievedData = client.get(cid)

        // Then: Should get the original data
        assertNotNull(retrievedData)
        assertEquals(originalData.component1(), retrievedData!!.component1())
        
        // Verify data integrity
        for (i in 0 until originalData.component1()) {
            assertEquals(originalData.component2()(i), retrievedData.component2()(i))
        }
    }

    @Test
    fun `test large file chunking`() = runTest {
        // Given: An IPFS client and large data
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val largeData = \1 j { \2: Int -> (i % 256).toByte() } // 500KB
        val chunkSize = 262144 // 256KB

        // When: Adding large file with chunking
        val cid = client.addFile(largeData, chunkSize = chunkSize)

        // Then: Should get a valid CID
        assertNotNull(cid)
        assertTrue(cid.version >= 0)

        // When: Retrieving the file
        val retrievedData = client.getFile(cid)

        // Then: Should get the complete file
        assertNotNull(retrievedData)
        assertEquals(largeData.component1(), retrievedData!!.component1())
        
        // Verify data integrity
        for (i in 0 until largeData.component1()) {
            assertEquals(largeData.component2()(i), retrievedData.component2()(i))
        }
    }

    @Test
    fun `test content pinning`() = runTest {
        // Given: An IPFS client with content
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = client.add(testData)

        // When: Pinning content
        val pinSuccess = client.pin(cid)

        // Then: Pinning should succeed
        assertTrue(pinSuccess)

        // When: Listing pinned content
        val pinnedCids = client.listPinned()

        // Then: Should contain the pinned CID
        assertTrue(pinnedCids.component1() > 0)
        var found = false
        for (i in 0 until pinnedCids.component1()) {
            if (pinnedCids.component2()(i) == cid) {
                found = true
                break
            }
        }
        assertTrue(found)
    }

    @Test
    fun `test content unpinning`() = runTest {
        // Given: An IPFS client with pinned content
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = client.add(testData)
        client.pin(cid)

        // When: Unpinning content
        val unpinSuccess = client.unpin(cid)

        // Then: Unpinning should succeed
        assertTrue(unpinSuccess)

        // When: Listing pinned content
        val pinnedCids = client.listPinned()

        // Then: Should not contain the unpinned CID
        var found = false
        for (i in 0 until pinnedCids.component1()) {
            if (pinnedCids.component2()(i) == cid) {
                found = true
                break
            }
        }
        assertFalse(found)
    }

    @Test
    fun `test garbage collection`() = runTest {
        // Given: An IPFS client with unpinned content
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val testData1 = \1 j { \2: Int -> (i % 256).toByte() }
        val testData2 = \1 j { \2: Int -> ((i + 100) % 256).toByte() }
        
        val cid1 = client.add(testData1)
        val cid2 = client.add(testData2)
        
        // Pin only the second content
        client.pin(cid2)

        // When: Running garbage collection
        val collectedCids = client.gc()

        // Then: Should collect unpinned content
        assertTrue(collectedCids.component1() > 0)
        var cid1Collected = false
        for (i in 0 until collectedCids.component1()) {
            if (collectedCids.component2()(i) == cid1) {
                cid1Collected = true
                break
            }
        }
        assertTrue(cid1Collected)

        // Pinned content should still be available
        val retrievedData2 = client.get(cid2)
        assertNotNull(retrievedData2)
        assertEquals(testData2.component1(), retrievedData2!!.component1())
    }

    @Test
    fun `test IPFS server API endpoints`() = runTest {
        // Given: An IPFS server
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)

        // When: Starting the server
        server.start()

        // Then: Server should be running
        val stats = server.getStats()
        assertTrue(stats.uptime >= 0)
        assertEquals(0, stats.peersCount)
        assertEquals(0, stats.blocksStored)
        assertEquals(0, stats.blocksRetrieved)
    }

    @Test
    fun `test IPFS HTTP API add endpoint`() = runTest {
        // Given: An IPFS server
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)
        server.start()

        val testData = \1 j { \2: Int -> (i % 256).toByte() }

        // When: Adding content via HTTP API
        val request = IpfsApiRequest(
            method = "POST",
            path = "/api/v0/add",
            body = testData
        )
        val response = server.handleApiRequest(request)

        // Then: Should get successful response
        assertEquals(200, response.status)
        assertTrue(response.data is Map<*, *>)
        val responseMap = response.data as Map<*, *>
        assertTrue(responseMap.containsKey("Hash"))
        
        val cidString = responseMap["Hash"] as String
        assertTrue(cidString.isNotEmpty())
    }

    @Test
    fun `test IPFS HTTP API cat endpoint`() = runTest {
        // Given: An IPFS server with content
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)
        server.start()

        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = server.add(testData)

        // When: Getting content via HTTP API
        val request = IpfsApiRequest(
            method = "GET",
            path = "/api/v0/cat",
            params = mapOf("arg" to cid.encode())
        )
        val response = server.handleApiRequest(request)

        // Then: Should get the content
        assertEquals(200, response.status)
        assertTrue(response.data is Indexed<*>)
        val retrievedData = response.data as Indexed<Byte>
        assertEquals(testData.component1(), retrievedData.component1())
    }

    @Test
    fun `test IPFS HTTP API pin endpoints`() = runTest {
        // Given: An IPFS server with content
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)
        server.start()

        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = server.add(testData)

        // When: Pinning content via HTTP API
        val pinRequest = IpfsApiRequest(
            method = "POST",
            path = "/api/v0/pin/add",
            params = mapOf("arg" to cid.encode())
        )
        val pinResponse = server.handleApiRequest(pinRequest)

        // Then: Should get successful response
        assertEquals(200, pinResponse.status)
        assertTrue(pinResponse.data is Map<*, *>)
        val pinResponseMap = pinResponse.data as Map<*, *>
        assertTrue(pinResponseMap.containsKey("Pins"))

        // When: Listing pins via HTTP API
        val listRequest = IpfsApiRequest(
            method = "GET",
            path = "/api/v0/pin/ls"
        )
        val listResponse = server.handleApiRequest(listRequest)

        // Then: Should get pinned content list
        assertEquals(200, listResponse.status)
        assertTrue(listResponse.data is Map<*, *>)
        val listResponseMap = listResponse.data as Map<*, *>
        assertTrue(listResponseMap.containsKey("Keys"))

        // When: Unpinning content via HTTP API
        val unpinRequest = IpfsApiRequest(
            method = "DELETE",
            path = "/api/v0/pin/rm",
            params = mapOf("arg" to cid.encode())
        )
        val unpinResponse = server.handleApiRequest(unpinRequest)

        // Then: Should get successful response
        assertEquals(200, unpinResponse.status)
        assertTrue(unpinResponse.data is Map<*, *>)
        val unpinResponseMap = unpinResponse.data as Map<*, *>
        assertTrue(unpinResponseMap.containsKey("Pins"))
    }

    @Test
    fun `test IPFS HTTP API version endpoint`() = runTest {
        // Given: An IPFS server
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)
        server.start()

        // When: Getting version via HTTP API
        val request = IpfsApiRequest(
            method = "GET",
            path = "/api/v0/version"
        )
        val response = server.handleApiRequest(request)

        // Then: Should get version information
        assertEquals(200, response.status)
        assertTrue(response.data is Map<*, *>)
        val responseMap = response.data as Map<*, *>
        assertTrue(responseMap.containsKey("Version"))
        assertTrue(responseMap.containsKey("Commit"))
        assertTrue(responseMap.containsKey("Repo"))
        assertTrue(responseMap.containsKey("System"))
    }

    @Test
    fun `test IPFS HTTP API id endpoint`() = runTest {
        // Given: An IPFS server
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)
        server.start()

        // When: Getting peer ID via HTTP API
        val request = IpfsApiRequest(
            method = "GET",
            path = "/api/v0/id"
        )
        val response = server.handleApiRequest(request)

        // Then: Should get peer information
        assertEquals(200, response.status)
        assertTrue(response.data is Map<*, *>)
        val responseMap = response.data as Map<*, *>
        assertTrue(responseMap.containsKey("ID"))
        assertTrue(responseMap.containsKey("Addresses"))
        assertTrue(responseMap.containsKey("AgentVersion"))
        assertTrue(responseMap.containsKey("ProtocolVersion"))
    }

    @Test
    fun `test torrent to IPFS gateway mapping`() = runTest {
        // Given: Torrent and IPFS data
        val torrentHash = \1 j { \2: Int -> i.toByte() }
        val ipfsCid = CID(1, CID.Codec.RAW, Multihash(Multihash.HashType.SHA2_256, \1 j { \2: Int -> i.toByte() }))
        val torrentName = "test-torrent"
        val totalSize = 1024 * 1024L

        // When: Creating gateway mapping
        val mapping = TorrentIpfsGatewayMapping(
            torrentHash = torrentHash,
            ipfsCid = ipfsCid,
            torrentName = torrentName,
            totalSize = totalSize
        )

        // Then: Mapping should be properly constructed
        assertEquals(torrentHash, mapping.torrentHash)
        assertEquals(ipfsCid, mapping.ipfsCid)
        assertEquals(torrentName, mapping.torrentName)
        assertEquals(totalSize, mapping.totalSize)
        assertTrue(mapping.createdAt > 0)
    }

    @Test
    fun `test IPFS content store and retrieve`() = runTest {
        // Given: An IPFS client
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val testData = \1 j { \2: Int -> (i % 256).toByte() }

        // When: Storing content
        val storeResult = client.store(testData)

        // Then: Should get store result
        assertNotNull(storeResult)
        assertTrue(storeResult.hash.isNotEmpty())

        // When: Retrieving content
        val retrieveResult = client.retrieve(storeResult.hash)

        // Then: Should get the original content
        assertNotNull(retrieveResult)
        assertEquals(testData.component1(), retrieveResult!!.content.component1())
        
        // Verify data integrity
        for (i in 0 until testData.component1()) {
            assertEquals(testData.component2()(i), retrieveResult.content.component2()(i))
        }
    }

    @Test
    fun `test IPFS content retrieval with invalid hash`() = runTest {
        // Given: An IPFS client
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        // When: Retrieving content with invalid hash
        val retrieveResult = client.retrieve("invalid-hash")

        // Then: Should return null
        assertNull(retrieveResult)
    }

    @Test
    fun `test IPFS server statistics tracking`() = runTest {
        // Given: An IPFS server
        val config = IpfsServerConfig()
        val storage = InMemoryIpfsStorage()
        val dht = MockDHTService()
        val pubsub = EnhancedIpfsPubSubService()
        
        val server = IpfsServer(config, storage, dht, pubsub)

        // When: Starting the server
        server.start()

        // When: Adding content
        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        server.add(testData)

        // When: Getting content
        val cid = server.add(testData)
        server.get(cid)

        // Then: Statistics should be updated
        val stats = server.getStats()
        assertTrue(stats.blocksStored > 0)
        assertTrue(stats.blocksRetrieved > 0)
        assertTrue(stats.bytesStored > 0)
        assertTrue(stats.bytesRetrieved > 0)
    }

    // === MOCK IMPLEMENTATIONS ===

    internal class MockDHTService : DHTService {
        override suspend fun start(localPeerId: PeerId) {}
        override suspend fun stop() {}
        override suspend fun provide(cid: CID) {}
        override suspend fun findProviders(cid: CID): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
        override suspend fun discoverPeers(): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
    }

    // === DATA CLASSES ===

    data class TorrentIpfsGatewayMapping(
        val torrentHash: InfoHash,
        val ipfsCid: CID,
        val torrentName: String,
        val totalSize: Long,
        val createdAt: Long = System.currentTimeMillis()
    )
} 