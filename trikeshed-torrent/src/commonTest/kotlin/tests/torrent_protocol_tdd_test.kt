package tests

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * TDD Tests for BitTorrent Protocol Implementation
 * 
 * Tests the core BitTorrent peer wire protocol functionality:
 * - Handshake protocol
 * - Message serialization/deserialization
 * - Peer connection management
 * - Piece transfer and verification
 */
class TorrentProtocolTddTest {

    @Test
    fun `test handshake message creation`() = runTest {
        // Given: Protocol parameters
        val protocol = "BitTorrent protocol"
        val reserved = ByteArray(8) { 0 }
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })

        // When: Creating handshake message
        val handshake = BitTorrentPeerWire.PeerMessage.Handshake(
            protocol = protocol,
            reserved = reserved,
            infoHash = infoHash,
            peerId = peerId
        )

        // Then: Handshake should be properly constructed
        assertEquals(protocol, handshake.protocol)
        assertEquals(8, handshake.reserved.size)
        assertEquals(infoHash, handshake.infoHash)
        assertEquals(peerId, handshake.peerId)
    }

    @Test
    fun `test message type enumeration`() = runTest {
        // Given: Message types
        val messageTypes = BitTorrentPeerWire.MessageType.values()

        // When: Testing message type mapping
        // Then: All message types should be mappable
        assertEquals(BitTorrentPeerWire.MessageType.CHOKE, BitTorrentPeerWire.MessageType.fromId(0))
        assertEquals(BitTorrentPeerWire.MessageType.UNCHOKE, BitTorrentPeerWire.MessageType.fromId(1))
        assertEquals(BitTorrentPeerWire.MessageType.INTERESTED, BitTorrentPeerWire.MessageType.fromId(2))
        assertEquals(BitTorrentPeerWire.MessageType.NOT_INTERESTED, BitTorrentPeerWire.MessageType.fromId(3))
        assertEquals(BitTorrentPeerWire.MessageType.HAVE, BitTorrentPeerWire.MessageType.fromId(4))
        assertEquals(BitTorrentPeerWire.MessageType.BITFIELD, BitTorrentPeerWire.MessageType.fromId(5))
        assertEquals(BitTorrentPeerWire.MessageType.REQUEST, BitTorrentPeerWire.MessageType.fromId(6))
        assertEquals(BitTorrentPeerWire.MessageType.PIECE, BitTorrentPeerWire.MessageType.fromId(7))
        assertEquals(BitTorrentPeerWire.MessageType.CANCEL, BitTorrentPeerWire.MessageType.fromId(8))
        assertEquals(BitTorrentPeerWire.MessageType.PORT, BitTorrentPeerWire.MessageType.fromId(9))
        assertEquals(BitTorrentPeerWire.MessageType.EXTENSION, BitTorrentPeerWire.MessageType.fromId(20))
        assertNull(BitTorrentPeerWire.MessageType.fromId(99)) // Invalid message type
    }

    @Test
    fun `test peer connection state management`() = runTest {
        // Given: A peer connection
        val address = "127.0.0.1"
        val port = 6881
        val connectionId = "conn-123"
        val context = Dispatchers.Unconfined
        val inputChannel = Channel<BitTorrentPeerWire.PeerMessage>(capacity = 10)
        val outputChannel = Channel<BitTorrentPeerWire.PeerMessage>(capacity = 10)
        val scope = CoroutineScope(context)

        val connection = BitTorrentPeerWire.PeerConnection(
            address = address,
            port = port,
            connectionId = connectionId,
            context = context,
            inputChannel = inputChannel,
            outputChannel = outputChannel,
            scope = scope
        )

        // When: Initializing connection
        // Then: Connection should have default state
        assertEquals(address, connection.address)
        assertEquals(port, connection.port)
        assertEquals(connectionId, connection.connectionId)
        assertTrue(connection.isChoked) // Default: choked
        assertFalse(connection.isInterested) // Default: not interested
        assertTrue(connection.amChoked) // Default: am choked
        assertFalse(connection.amInterested) // Default: am not interested
        assertEquals(0, connection.bitfield.size)
        assertTrue(connection.pieces.isEmpty())
        assertEquals(0L, connection.downloadSpeed)
        assertEquals(0L, connection.uploadSpeed)

        // When: Updating connection state
        connection.isChoked = false
        connection.isInterested = true
        connection.amChoked = false
        connection.amInterested = true
        connection.downloadSpeed = 1024 * 1024 // 1MB/s
        connection.uploadSpeed = 512 * 1024 // 512KB/s

        // Then: State should be updated
        assertFalse(connection.isChoked)
        assertTrue(connection.isInterested)
        assertFalse(connection.amChoked)
        assertTrue(connection.amInterested)
        assertEquals(1024 * 1024L, connection.downloadSpeed)
        assertEquals(512 * 1024L, connection.uploadSpeed)
    }

    @Test
    fun `test bitfield message handling`() = runTest {
        // Given: A bitfield message
        val pieceCount = 100
        val pieces = BooleanArray(pieceCount) { i -> i % 2 == 0 } // Even pieces available
        val bitfieldMessage = BitTorrentPeerWire.PeerMessage.Bitfield(pieces = pieces)

        // When: Creating bitfield
        // Then: Bitfield should be correct
        assertEquals(pieceCount, bitfieldMessage.pieces.size)
        assertTrue(bitfieldMessage.pieces[0]) // Piece 0 available
        assertFalse(bitfieldMessage.pieces[1]) // Piece 1 not available
        assertTrue(bitfieldMessage.pieces[2]) // Piece 2 available
        assertFalse(bitfieldMessage.pieces[3]) // Piece 3 not available

        // When: Counting available pieces
        val availableCount = bitfieldMessage.pieces.count { it }
        // Then: Should have 50 available pieces (even indices)
        assertEquals(50, availableCount)
    }

    @Test
    fun `test request message creation`() = runTest {
        // Given: Request parameters
        val pieceIndex = 42
        val offset = 1024
        val length = 16384

        // When: Creating request message
        val requestMessage = BitTorrentPeerWire.PeerMessage.Request(
            pieceIndex = pieceIndex,
            offset = offset,
            length = length
        )

        // Then: Request should be properly constructed
        assertEquals(pieceIndex, requestMessage.pieceIndex)
        assertEquals(offset, requestMessage.offset)
        assertEquals(length, requestMessage.length)
    }

    @Test
    fun `test piece message creation`() = runTest {
        // Given: Piece data
        val pieceIndex = 5
        val offset = 512
        val pieceData = \1 j { \2: Int -> (i % 256).toByte() }

        // When: Creating piece message
        val pieceMessage = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = pieceIndex,
            offset = offset,
            data = pieceData
        )

        // Then: Piece message should be properly constructed
        assertEquals(pieceIndex, pieceMessage.pieceIndex)
        assertEquals(offset, pieceMessage.offset)
        assertEquals(pieceData, pieceMessage.data)
        assertEquals(16384, pieceMessage.data.component1())
    }

    @Test
    fun `test peer wire protocol initialization`() = runTest {
        // Given: Protocol parameters
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val port = 6881

        // When: Creating peer wire protocol
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId,
            port = port
        )

        // Then: Protocol should be initialized
        assertNotNull(peerWire)
        // Note: We can't directly access internal fields, but we can test public methods
    }

    @Test
    fun `test connection lifecycle`() = runTest {
        // Given: A peer wire protocol instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        // When: Adding a connection
        val peerAddress = "127.0.0.1:6881"
        val connection = peerWire.addConnection(peerAddress, 6881)

        // Then: Connection should be established
        assertNotNull(connection)
        assertEquals(peerAddress, connection.address)
        assertEquals(6881, connection.port)

        // When: Getting connections
        val connections = peerWire.getConnections()

        // Then: Should have one connection
        assertEquals(1, connections.size)
        assertEquals(peerAddress, connections[0].address)

        // When: Closing connections
        peerWire.close()

        // Then: All connections should be closed
        // Note: In a real implementation, we'd verify connections are closed
    }

    @Test
    fun `test piece verification workflow`() = runTest {
        // Given: A peer wire protocol instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        // When: Receiving multiple pieces
        val piece1 = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = 0,
            offset = 0,
            data = \1 j { \2: Int -> (i % 256).toByte() }
        )
        val piece2 = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = 1,
            offset = 0,
            data = \1 j { \2: Int -> ((i + 100) % 256).toByte() }
        )

        peerWire.handleMessage("127.0.0.1:6881", piece1)
        peerWire.handleMessage("127.0.0.1:6881", piece2)

        // Then: Both pieces should be verified
        val verifiedPieces = peerWire.getVerifiedPieces()
        assertTrue(verifiedPieces.contains(0))
        assertTrue(verifiedPieces.contains(1))
        assertEquals(2, verifiedPieces.size)
    }

    @Test
    fun `test message handling for different types`() = runTest {
        // Given: A peer wire protocol instance
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        // When: Handling different message types
        val haveMessage = BitTorrentPeerWire.PeerMessage.Have(pieceIndex = 42)
        val interestedMessage = BitTorrentPeerWire.PeerMessage.Interested
        val notInterestedMessage = BitTorrentPeerWire.PeerMessage.NotInterested
        val chokeMessage = BitTorrentPeerWire.PeerMessage.Choke
        val unchokeMessage = BitTorrentPeerWire.PeerMessage.Unchoke

        // Then: All messages should be handled without errors
        peerWire.handleMessage("127.0.0.1:6881", haveMessage)
        peerWire.handleMessage("127.0.0.1:6881", interestedMessage)
        peerWire.handleMessage("127.0.0.1:6881", notInterestedMessage)
        peerWire.handleMessage("127.0.0.1:6881", chokeMessage)
        peerWire.handleMessage("127.0.0.1:6881", unchokeMessage)

        // Verify no exceptions were thrown
        assertTrue(true) // If we get here, no exceptions occurred
    }

    @Test
    fun `test piece data assembly`() = runTest {
        // Given: Multiple piece blocks for the same piece
        val pieceIndex = 5
        val block1 = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = pieceIndex,
            offset = 0,
            data = \1 j { \2: Int -> (i % 256).toByte() }
        )
        val block2 = BitTorrentPeerWire.PeerMessage.Piece(
            pieceIndex = pieceIndex,
            offset = 8192,
            data = \1 j { \2: Int -> ((i + 8192) % 256).toByte() }
        )

        // When: Receiving both blocks
        val infoHash = \1 j { \2: Int -> i.toByte() }
        val peerId = \1 j { \2: Int -> (i + 10).toByte() })
        val peerWire = BitTorrentPeerWire(
            context = Dispatchers.Unconfined,
            infoHash = infoHash,
            peerId = peerId
        )

        peerWire.handleMessage("127.0.0.1:6881", block1)
        peerWire.handleMessage("127.0.0.1:6881", block2)

        // Then: Piece should be complete and verified
        val verifiedPieces = peerWire.getVerifiedPieces()
        assertTrue(verifiedPieces.contains(pieceIndex))
    }

    @Test
    fun `test protocol constants`() = runTest {
        // Given: Protocol constants
        // When: Accessing constants
        // Then: Constants should have expected values
        assertEquals("BitTorrent protocol", BitTorrentPeerWire.PROTOCOL_STRING)
        assertEquals(68, BitTorrentPeerWire.HANDSHAKE_LENGTH)
        assertEquals(4, BitTorrentPeerWire.MESSAGE_LENGTH_SIZE)
        assertEquals(1, BitTorrentPeerWire.MESSAGE_ID_SIZE)
        assertEquals(16384, BitTorrentPeerWire.PIECE_SIZE)
        assertEquals(16384, BitTorrentPeerWire.MAX_PIECE_SIZE)
        assertEquals(16384, BitTorrentPeerWire.REQUEST_SIZE)
    }

    @Test
    fun `test extension message handling`() = runTest {
        // Given: An extension message
        val messageId = 1.toByte()
        val payload = \1 j { \2: Int -> (i % 256).toByte() }
        val extensionMessage = BitTorrentPeerWire.PeerMessage.Extension(
            messageId = messageId,
            payload = payload
        )

        // When: Creating extension message
        // Then: Extension should be properly constructed
        assertEquals(messageId, extensionMessage.messageId)
        assertEquals(payload, extensionMessage.payload)
        assertEquals(256, extensionMessage.payload.component1())
    }

    @Test
    fun `test port message handling`() = runTest {
        // Given: A port message
        val port = 6881
        val portMessage = BitTorrentPeerWire.PeerMessage.Port(port = port)

        // When: Creating port message
        // Then: Port should be properly set
        assertEquals(port, portMessage.port)
    }

    @Test
    fun `test cancel message handling`() = runTest {
        // Given: A cancel message
        val pieceIndex = 10
        val offset = 1024
        val length = 16384
        val cancelMessage = BitTorrentPeerWire.PeerMessage.Cancel(
            pieceIndex = pieceIndex,
            offset = offset,
            length = length
        )

        // When: Creating cancel message
        // Then: Cancel should be properly constructed
        assertEquals(pieceIndex, cancelMessage.pieceIndex)
        assertEquals(offset, cancelMessage.offset)
        assertEquals(length, cancelMessage.length)
    }
} 