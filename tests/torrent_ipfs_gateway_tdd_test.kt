package tests

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.*
import borg.trikeshed.torrent.protocol.*
import borg.trikeshed.torrent.simulation.*
import borg.trikeshed.torrent.rpc.*
import borg.trikeshed.torrent.batch.*
import borg.trikeshed.ipfs.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Comprehensive TDD Test Suite for TrikeShed Torrent Implementation and IPFS Gateway
 * 
 * This test suite follows TDD principles:
 * 1. Write failing tests first
 * 2. Implement minimal code to make tests pass
 * 3. Refactor while keeping tests green
 * 4. Repeat for new functionality
 */
class TorrentIpfsGatewayTddTest {

    // === TORRENT PROTOCOL TESTS ===

    @Test
    fun `test BitTorrent handshake protocol`() = runTest {
        // Given: A BitTorrent peer wire instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId,
            port = 6881
        )

        // When: Creating a handshake message
        val handshake = BitTorrentPeerWire.PeerMessage.Handshake(
            protocol = "BitTorrent protocol",
            reserved = ByteArray(8),
            infoHash = infoHash,
            peerId = peerId
        )

        // Then: Handshake should be valid
        assertEquals("BitTorrent protocol", handshake.protocol)
        assertEquals(infoHash, handshake.infoHash)
        assertEquals(peerId, handshake.peerId)
        assertEquals(8, handshake.reserved.size)
    }

    @Test
    fun `test peer message serialization`() = runTest {
        // Given: Various peer messages
        val haveMessage = BitTorrentPeerWire.PeerMessage.Have(pieceIndex = 42)
        val requestMessage = BitTorrentPeerWire.PeerMessage.Request(
            pieceIndex = 10,
            offset = 1024,
            length = 16384
        )
        val pieceData = \1 j { \2: Int -> (i % 256).toByte() }
        val pieceMessage = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = 5,
            offset = 512,
            data = pieceData
        )

        // When: Creating messages
        // Then: Messages should have correct properties
        assertEquals(42, haveMessage.pieceIndex)
        assertEquals(10, requestMessage.pieceIndex)
        assertEquals(1024, requestMessage.offset)
        assertEquals(16384, requestMessage.length)
        assertEquals(5, pieceMessage.pieceIndex)
        assertEquals(512, pieceMessage.offset)
        assertEquals(16384, pieceMessage.data.component1())
    }

    @Test
    fun `test peer connection management`() = runTest {
        // Given: A BitTorrent peer wire instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        // When: Adding a peer connection
        val peerAddress = "127.0.0.1:6881"
        val connection = peerWire.addConnection(peerAddress, 6881)

        // Then: Connection should be established
        assertNotNull(connection)
        assertEquals(peerAddress, connection.address)
        assertEquals(6881, connection.port)
        assertFalse(connection.isChoked)
        assertFalse(connection.isInterested)
        assertTrue(connection.amChoked)
        assertFalse(connection.amInterested)

        // When: Getting connections
        val connections = peerWire.getConnections()

        // Then: Should have one connection
        assertEquals(1, connections.size)
        assertEquals(peerAddress, connections[0].address)
    }

    @Test
    fun `test piece verification`() = runTest {
        // Given: A BitTorrent peer wire instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        // When: Receiving a piece
        val pieceData = \1 j { \2: Int -> (i % 256).toByte() }
        val pieceIndex = 5
        val offset = 0
        val pieceMessage = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = pieceIndex,
            offset = offset,
            data = pieceData
        )

        // Then: Piece should be stored
        peerWire.handleMessage("127.0.0.1:6881", pieceMessage)
        val verifiedPieces = peerWire.getVerifiedPieces()
        assertTrue(verifiedPieces.contains(pieceIndex))
    }

    // === TORRENT SIMULATION TESTS ===

    @Test
    fun `test torrent simulation creation`() = runTest {
        // Given: A torrent simulation
        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)

        // When: Starting the simulation
        simulation.start()

        // Then: Simulation should be running
        assertTrue(simulation.isRunning())

        // When: Creating a torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "test-torrent",
            totalSize = 1024 * 1024, // 1MB
            pieceSize = 16384
        )

        // Then: Torrent should be created
        assertEquals("test-torrent", torrent.name)
        assertEquals(infoHash, torrent.infoHash)
        assertEquals(1024 * 1024, torrent.totalSize)
        assertEquals(16384, torrent.pieceSize)
        assertEquals(64, torrent.totalPieces) // 1MB / 16KB = 64 pieces
    }

    @Test
    fun `test peer joining simulation`() = runTest {
        // Given: A torrent simulation with a torrent
        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
        simulation.start()
        
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "test-torrent",
            totalSize = 1024 * 1024,
            pieceSize = 16384
        )

        // When: A peer joins the torrent
        val peerId = \1 j { \2: Int -> (i + 20).toByte() })
        val peer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = peerId,
            address = "127.0.0.1",
            port = 6882,
            hasPieces = setOf(0, 1, 2, 3)
        )

        // Then: Peer should be added
        assertEquals(peerId, peer.peerId)
        assertEquals("127.0.0.1", peer.address)
        assertEquals(6882, peer.port)
        assertEquals(4, peer.hasPieces.size)
        assertTrue(peer.hasPieces.contains(0))
        assertTrue(peer.hasPieces.contains(1))
        assertTrue(peer.hasPieces.contains(2))
        assertTrue(peer.hasPieces.contains(3))
    }

    @Test
    fun `test piece transfer simulation`() = runTest {
        // Given: A torrent simulation with torrent and peers
        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
        simulation.start()
        
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "test-torrent",
            totalSize = 1024 * 1024,
            pieceSize = 16384
        )

        val peer1 = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i + 20).toByte() }),
            address = "127.0.0.1",
            port = 6882,
            hasPieces = setOf(0, 1, 2, 3)
        )

        val peer2 = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i + 30).toByte() }),
            address = "127.0.0.1",
            port = 6883,
            hasPieces = emptySet()
        )

        // When: Simulating piece transfer
        val pieceIndex = 1
        val pieceData = \1 j { \2: Int -> (i % 256).toByte() }
        simulation.simulatePieceTransfer(
            torrent = torrent,
            fromPeer = peer1,
            toPeer = peer2,
            pieceIndex = pieceIndex,
            pieceData = pieceData
        )

        // Then: Peer2 should have the piece
        val updatedPeer2 = simulation.getPeer(torrent, peer2.peerId)
        assertNotNull(updatedPeer2)
        assertTrue(updatedPeer2!!.hasPieces.contains(pieceIndex))
    }

    // === IPFS GATEWAY TESTS ===

    @Test
    fun `test IPFS client content storage`() = runTest {
        // Given: An IPFS client with in-memory storage
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        // When: Adding content to IPFS
        val testData = \1 j { \2: Int -> (i % 256).toByte() }
        val cid = client.add(testData)

        // Then: Content should be stored and retrievable
        assertNotNull(cid)
        val retrievedData = client.get(cid)
        assertNotNull(retrievedData)
        assertEquals(testData.component1(), retrievedData!!.component1())
        
        // Verify data integrity
        for (i in 0 until testData.component1()) {
            assertEquals(testData.component2()(i), retrievedData.component2()(i))
        }
    }

    @Test
    fun `test IPFS client file chunking`() = runTest {
        // Given: An IPFS client with large file data
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val client = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        // When: Adding a large file (larger than chunk size)
        val largeData = \1 j { \2: Int -> (i % 256).toByte() } // 300KB
        val cid = client.addFile(largeData, chunkSize = 262144) // 256KB chunks

        // Then: File should be chunked and stored
        assertNotNull(cid)
        val retrievedData = client.getFile(cid)
        assertNotNull(retrievedData)
        assertEquals(largeData.component1(), retrievedData!!.component1())
        
        // Verify data integrity
        for (i in 0 until largeData.component1()) {
            assertEquals(largeData.component2()(i), retrievedData.component2()(i))
        }
    }

    @Test
    fun `test IPFS pinning operations`() = runTest {
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

        // Then: Content should be pinned
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

        // When: Unpinning content
        val unpinSuccess = client.unpin(cid)

        // Then: Content should be unpinned
        assertTrue(unpinSuccess)
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

        // When: Adding content via API
        val testData = \1 j { \2: Int -> (i % 256).toByte() }
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

        // When: Getting content via API
        val cidString = responseMap["Hash"] as String
        val getRequest = IpfsApiRequest(
            method = "GET",
            path = "/api/v0/cat",
            params = mapOf("arg" to cidString)
        )
        val getResponse = server.handleApiRequest(getRequest)

        // Then: Should get the content
        assertEquals(200, getResponse.status)
        assertTrue(getResponse.data is Indexed<*>)
        val retrievedData = getResponse.data as Indexed<Byte>
        assertEquals(testData.component1(), retrievedData.component1())
    }

    // === TORRENT-IPFS GATEWAY TESTS ===

    @Test
    fun `test torrent to IPFS gateway integration`() = runTest {
        // Given: A torrent simulation and IPFS client
        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
        simulation.start()
        
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val ipfsClient = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        // When: Creating a torrent and downloading it
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "test-torrent",
            totalSize = 1024 * 1024,
            pieceSize = 16384
        )

        val peer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i + 20).toByte() }),
            address = "127.0.0.1",
            port = 6882,
            hasPieces = (0..63).toSet() // Has all pieces
        )

        // Simulate downloading all pieces
        val downloadedPieces = mutableListOf<Indexed<Byte>>()
        for (i in 0 until torrent.totalPieces) {
            val pieceData = \1 j { \2: Int -> ((i * 16384 + j) % 256).toByte() }
            downloadedPieces.add(pieceData)
        }

        // When: Storing torrent content in IPFS
        val torrentData = \1 j { \2: Int ->
            val newSize = acc.component1() + piece.component1()
            \1 j { \2: Int ->
                if (i < acc.component1()) acc.component2()(i) else piece.component2()(i - acc.component1())
            }
        }
        val ipfsCid = ipfsClient.add(torrentData)

        // Then: Content should be stored in IPFS
        assertNotNull(ipfsCid)
        val retrievedData = ipfsClient.get(ipfsCid)
        assertNotNull(retrievedData)
        assertEquals(torrentData.component1(), retrievedData!!.component1())

        // When: Creating a gateway mapping
        val gatewayMapping = TorrentIpfsGatewayMapping(
            torrentHash = infoHash,
            ipfsCid = ipfsCid,
            torrentName = torrent.name,
            totalSize = torrent.totalSize
        )

        // Then: Gateway mapping should be valid
        assertEquals(infoHash, gatewayMapping.torrentHash)
        assertEquals(ipfsCid, gatewayMapping.ipfsCid)
        assertEquals(torrent.name, gatewayMapping.torrentName)
        assertEquals(torrent.totalSize, gatewayMapping.totalSize)
    }

    @Test
    fun `test IPFS to torrent seeding`() = runTest {
        // Given: IPFS content and torrent simulation
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val ipfsClient = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
        simulation.start()

        // When: Adding content to IPFS first
        val testData = 1024 * \1 j { \2: Int -> (i % 256).toByte() } // 1MB
        val ipfsCid = ipfsClient.add(testData)

        // Then: Content should be in IPFS
        assertNotNull(ipfsCid)
        val retrievedData = ipfsClient.get(ipfsCid)
        assertNotNull(retrievedData)

        // When: Creating a torrent from IPFS content
        val infoHash = \1 j { \2: Int -> (i + 100).toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "ipfs-seeded-torrent",
            totalSize = testData.component1().toLong(),
            pieceSize = 16384
        )

        // When: A peer joins to download
        val downloaderPeer = simulation.simulatePeerJoin(
            torrent = torrent,
            peerId = \1 j { \2: Int -> (i + 200).toByte() }),
            address = "127.0.0.1",
            port = 6883,
            hasPieces = emptySet()
        )

        // When: IPFS gateway provides pieces
        val totalPieces = torrent.totalPieces
        for (i in 0 until totalPieces) {
            val pieceOffset = i * 16384
            val pieceSize = minOf(16384, testData.component1() - pieceOffset)
            val pieceData = \1 j { \2: Int -> testData.component2()(pieceOffset + j) }
            
            simulation.simulatePieceTransfer(
                torrent = torrent,
                fromPeer = null, // IPFS gateway
                toPeer = downloaderPeer,
                pieceIndex = i,
                pieceData = pieceData
            )
        }

        // Then: Downloader should have all pieces
        val updatedDownloader = simulation.getPeer(torrent, downloaderPeer.peerId)
        assertNotNull(updatedDownloader)
        assertEquals(totalPieces, updatedDownloader!!.hasPieces.size)
        for (i in 0 until totalPieces) {
            assertTrue(updatedDownloader.hasPieces.contains(i))
        }
    }

    // === RPC SERVER TESTS ===

    @Test
    fun `test torrent RPC server operations`() = runTest {
        // Given: A torrent RPC server
        val downloader = MockTrikeDownloader()
        val requestFactory = MockRequestFactoryService()
        val rpcServer = TorrentRpcServer(downloader, requestFactory)

        // When: Adding a torrent via RPC
        val torrentData = "d8:announce32:http://tracker.example.com:6881/announce13:creation datei1234567890e4:infod6:lengthi1024e4:name4:test12:piece lengthi16384e6:pieces20:01234567890123456789ee"
        val request = RpcRequest(
            id = "1",
            method = "aria2.addTorrent",
            params = mapOf(
                "torrent" to torrentData,
                "uris" to listOf("http://tracker.example.com:6881/announce")
            )
        )

        val response = rpcServer.handleRequest(request)

        // Then: Should get successful response
        assertEquals("1", response.id)
        assertNull(response.error)
        assertNotNull(response.result)
        assertTrue(response.result is String)

        // When: Getting status
        val downloadId = response.result as String
        val statusRequest = RpcRequest(
            id = "2",
            method = "aria2.tellStatus",
            params = mapOf("gid" to downloadId)
        )

        val statusResponse = rpcServer.handleRequest(statusRequest)

        // Then: Should get status information
        assertEquals("2", statusResponse.id)
        assertNull(statusResponse.error)
        assertNotNull(statusResponse.result)
        assertTrue(statusResponse.result is Map<*, *>)
    }

    // === BATCH CONTROL TESTS ===

    @Test
    fun `test torrent batch controller`() = runTest {
        // Given: A batch controller
        val config = BatchConfig(
            name = "test-batch",
            priority = BatchPriority.NORMAL,
            maxConcurrent = 3
        )
        val controller = TorrentBatchController("batch-1", config)

        // When: Adding torrents to batch
        val torrent1 = TorrentInfo(
            infoHash = "hash1",
            name = "torrent1",
            totalSize = 1024 * 1024,
            downloadedSize = 0,
            status = TorrentStatus.QUEUED,
            downloadSpeed = 0
        )
        val torrent2 = TorrentInfo(
            infoHash = "hash2",
            name = "torrent2",
            totalSize = 2048 * 1024,
            downloadedSize = 0,
            status = TorrentStatus.QUEUED,
            downloadSpeed = 0
        )

        controller.addTorrent(torrent1)
        controller.addTorrent(torrent2)

        // When: Starting the batch
        controller.start()

        // Then: Batch should be active
        val status = controller.getStatus()
        assertEquals(BatchState.ACTIVE, status.status)
        assertEquals(2, status.torrentCount)
        assertEquals(0, status.completedCount)

        // When: Updating torrent progress
        val updatedTorrent1 = torrent1.copy(
            downloadedSize = 512 * 1024,
            status = TorrentStatus.DOWNLOADING,
            downloadSpeed = 1024 * 1024 // 1MB/s
        )
        controller.addTorrent(updatedTorrent1)

        // Then: Progress should be updated
        val updatedStatus = controller.getStatus()
        assertEquals(0.25, updatedStatus.totalProgress, 0.01) // 25% complete
        assertTrue(updatedStatus.downloadSpeed > 0)
    }

    // === INTEGRATION TESTS ===

    @Test
    fun `test complete torrent to IPFS workflow`() = runTest {
        // Given: Complete system setup
        val simulation = TorrentSimulation(context = Dispatchers.Unconfined)
        simulation.start()
        
        val storage = InMemoryIpfsStorage()
        val peerId = \1 j { \2: Int -> i.toByte() })
        val ipfsClient = IpfsClient(
            localPeerId = peerId,
            quicEngine = null,
            storage = storage
        )

        val downloader = MockTrikeDownloader()
        val requestFactory = MockRequestFactoryService()
        val rpcServer = TorrentRpcServer(downloader, requestFactory)

        // When: Creating and downloading a torrent
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val torrent = simulation.createTorrent(
            infoHash = infoHash,
            name = "integration-test",
            totalSize = 512 * 1024, // 512KB
            pieceSize = 16384
        )

        // Simulate complete download
        val downloadedPieces = mutableListOf<Indexed<Byte>>()
        for (i in 0 until torrent.totalPieces) {
            val pieceData = \1 j { \2: Int -> ((i * 16384 + j) % 256).toByte() }
            downloadedPieces.add(pieceData)
        }

        // When: Storing in IPFS
        val torrentData = \1 j { \2: Int ->
            val newSize = acc.component1() + piece.component1()
            \1 j { \2: Int ->
                if (i < acc.component1()) acc.component2()(i) else piece.component2()(i - acc.component1())
            }
        }
        val ipfsCid = ipfsClient.add(torrentData)

        // When: Creating gateway mapping
        val gatewayMapping = TorrentIpfsGatewayMapping(
            torrentHash = infoHash,
            ipfsCid = ipfsCid,
            torrentName = torrent.name,
            totalSize = torrent.totalSize
        )

        // When: Registering with RPC server
        val registerRequest = RpcRequest(
            id = "1",
            method = "aria2.addTorrent",
            params = mapOf(
                "torrent" to "mock-torrent-data",
                "uris" to listOf("ipfs://${ipfsCid.encode()}")
            )
        )

        val response = rpcServer.handleRequest(registerRequest)

        // Then: Complete workflow should succeed
        assertNotNull(ipfsCid)
        assertNotNull(gatewayMapping)
        assertEquals("1", response.id)
        assertNull(response.error)
        assertNotNull(response.result)

        // Verify IPFS content integrity
        val retrievedData = ipfsClient.get(ipfsCid)
        assertNotNull(retrievedData)
        assertEquals(torrentData.component1(), retrievedData!!.component1())
    }

    // === MOCK IMPLEMENTATIONS ===

    internal class MockDHTService : DHTService {
        override suspend fun start(localPeerId: PeerId) {}
        override suspend fun stop() {}
        override suspend fun provide(cid: CID) {}
        override suspend fun findProviders(cid: CID): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
        override suspend fun discoverPeers(): Indexed<PeerInfo> = 0 j { throw NoSuchElementException() }
    }

    internal class MockTrikeDownloader {
        suspend fun addDownload(task: DownloadTask) {}
        suspend fun removeDownload(id: String): Boolean = true
        suspend fun pauseDownload(id: String): Boolean = true
        suspend fun resumeDownload(id: String): Boolean = true
        suspend fun stop() {}
    }

    internal class MockRequestFactoryService : RequestFactoryService {
        override suspend fun createRequest(method: String, params: Map<String, Any?>): RequestFactoryRequest {
            return RequestFactoryRequest("mock-id", method, params)
        }
        override suspend fun executeRequest(request: RequestFactoryRequest): RequestFactoryResponse {
            return RequestFactoryResponse("mock-id", true, mapOf("result" to "mock-result"))
        }
    }

    // === DATA CLASSES FOR GATEWAY INTEGRATION ===

    data class TorrentIpfsGatewayMapping(
        val torrentHash: InfoHash,
        val ipfsCid: CID,
        val torrentName: String,
        val totalSize: Long,
        val createdAt: Long = System.currentTimeMillis()
    )

    data class DownloadTask(
        val id: String,
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val timeout: Long = 30000L
    ) {
        sealed class HttpDownload(
            override val id: String,
            override val url: String,
            override val method: String,
            override val headers: Map<String, String>,
            override val timeout: Long
        ) : DownloadTask(id, url, method, headers, timeout)

        sealed class TorrentDownload(
            override val id: String,
            override val url: String,
            val trackers: List<String>,
            val pieceSize: Int
        ) : DownloadTask(id, url)
    }

    data class DownloadProgress(
        val id: String,
        val status: DownloadStatus,
        val totalBytes: Long,
        val downloadedBytes: Long,
        val uploadedBytes: Long,
        val bitfield: String,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val infoHash: String,
        val numSeeders: Int,
        val isSeeder: Boolean,
        val pieceLength: Int,
        val numPieces: Int,
        val connections: Int,
        val errorCode: Int,
        val errorMessage: String,
        val followedBy: List<String>,
        val following: List<String>,
        val belongsTo: String,
        val directory: String,
        val files: List<Map<String, Any>>,
        val bittorrent: Map<String, Any>,
        val verifiedLength: Long,
        val verifyIntegrityPending: Boolean
    )

    enum class DownloadStatus {
        ACTIVE, WAITING, PAUSED, ERROR, COMPLETE, REMOVED
    }

    data class RpcRequest(
        val id: String,
        val method: String,
        val params: Map<String, Any?> = emptyMap()
    )

    data class RpcResponse(
        val id: String,
        val result: Any? = null,
        val error: RpcError? = null
    ) {
        companion object {
            fun success(id: String, result: Any?): RpcResponse = RpcResponse(id, result)
            fun error(id: String, code: Int, message: String): RpcResponse = 
                RpcResponse(id, error = RpcError(code, message))
        }
    }

    data class RpcError(
        val code: Int,
        val message: String
    )

    data class TorrentInfo(
        val infoHash: String,
        val name: String,
        val totalSize: Long,
        val downloadedSize: Long,
        val status: TorrentStatus,
        val downloadSpeed: Long
    )

    enum class TorrentStatus {
        QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
    }

    data class BatchConfig(
        val name: String,
        val priority: BatchPriority,
        val maxConcurrent: Int
    )

    enum class BatchPriority {
        LOW, NORMAL, HIGH
    }

    enum class BatchState {
        QUEUED, ACTIVE, PAUSED, COMPLETED, ERROR
    }

    data class BatchStatus(
        val batchId: String,
        val name: String,
        val status: BatchState,
        val torrentCount: Int,
        val activeCount: Int,
        val completedCount: Int,
        val totalProgress: Double,
        val totalSize: Long,
        val downloadedSize: Long,
        val downloadSpeed: Long,
        val eta: kotlin.time.Duration?,
        val createdAt: Long,
        val updatedAt: Long
    )

    data class RequestFactoryRequest(
        val id: String,
        val method: String,
        val params: Map<String, Any?>
    )

    data class RequestFactoryResponse(
        val id: String,
        val success: Boolean,
        val data: Map<String, Any?>
    )

    interface RequestFactoryService {
        suspend fun createRequest(method: String, params: Map<String, Any?>): RequestFactoryRequest
        suspend fun executeRequest(request: RequestFactoryRequest): RequestFactoryResponse
    }
} 