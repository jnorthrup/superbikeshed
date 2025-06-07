package evolution

// Imports for new IO services
import evolution.io.PlatformIoService
import evolution.io.PlatformIoServiceKey
import evolution.io.PlatformUdpChannel
import evolution.io.PlatformUdpChannelKey // If we decide to put created channels in context
import evolution.io.SocketAddress
import evolution.io.InterestOp
import evolution.io.SelectionEvent

<<<<<<< HEAD
// Keep UdpSocket import for the deprecated function for now
import borg.trikeshed.reactor.UdpSocket
=======
// import borg.trikeshed.reactor.UdpSocket // REMOVED
>>>>>>> origin/jules_wip_6906935130323988499

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
<<<<<<< HEAD
// import kotlinx.coroutines.withContext // Not directly used at top-level after edit
=======
// Import new types from QuicSpecTypes.kt
import evolution.* // Using wildcard import for simplicity here
>>>>>>> origin/jules_wip_8844705664950451013

// Expect declarations for platform-specific dispatchers
expect fun getIODispatcher(): CoroutineDispatcher // Remains for now, not directly replaced

<<<<<<< HEAD
@Deprecated("Use PlatformIoService.createUdpChannel() instead.", replaceWith = ReplaceWith("coroutineContext[PlatformIoServiceKey]?.createUdpChannel()", "evolution.io.PlatformIoServiceKey"))
expect fun createUdpSocket(): UdpSocket
=======
// @Deprecated(...) expect fun createUdpSocket(): UdpSocket // REMOVED
>>>>>>> origin/jules_wip_6906935130323988499

// Base keys for simple QUIC context elements
expect object QuicConnectionContextKey : CoroutineContext.Key<QuicConnectionContextValue>
expect object QuicCryptoContextKey : CoroutineContext.Key<QuicCryptoContextValue>

<<<<<<< HEAD
@Deprecated("Replaced by direct use of PlatformUdpChannel and PlatformIoService from context.")
expect object QuicSocketContextKey : CoroutineContext.Key<QuicSocketContextValue>
=======
// @Deprecated(...) expect object QuicSocketContextKey ... // REMOVED
>>>>>>> origin/jules_wip_6906935130323988499

expect object QuicProtectionContextKey : CoroutineContext.Key<QuicProtectionContextValue>
expect object QuicObservabilityContextKey : CoroutineContext.Key<QuicObservabilityContextValue>

// Base context value interfaces (simple, common API)
expect interface QuicConnectionContextValue : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicConnectionContextKey
<<<<<<< HEAD
    val connection: QuicConnection
=======
    val connection: QuicConnection // Provides access to the mutable connection state
    // Assuming QuicPacket is a local type not from QuicSpecTypes.kt for now.
    // Payload for initial packet is often cryptographic handshake data.
>>>>>>> origin/jules_wip_8844705664950451013
    fun createInitialPacket(payload: ByteArray): QuicPacket
    // Payload for data packet is application stream data.
    fun createDataPacket(payload: StreamFrameData): QuicPacket
    fun transitionToEstablished()
}

expect interface QuicCryptoContextValue : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicCryptoContextKey
    // Handshake messages are byte arrays at this level.
    fun generateClientHello(serverName: String): ByteArray
    fun processServerResponse(response: ByteArray): Boolean
<<<<<<< HEAD
    suspend fun deriveInitialSecrets(context: CoroutineContext, clientDstConnId: ByteArray): QuicInitialKeys
=======
    // clientDstConnId is now strongly typed. QuicInitialKeys is not from QuicSpecTypes.kt.
    fun deriveInitialSecrets(clientDstConnId: ConnectionID): QuicInitialKeys
>>>>>>> origin/jules_wip_8844705664950451013
}

<<<<<<< HEAD
@Deprecated("Replaced by direct use of PlatformUdpChannel and PlatformIoService from context.")
expect interface QuicSocketContextValue : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicSocketContextKey
<<<<<<< HEAD
    val socket: UdpSocket // Old UdpSocket type
    val host: String
    val port: Int
    suspend fun sendPacket(packet: ByteArray): Boolean
=======
    val socket: UdpSocket
    val host: String // Hostname remains String
    val port: Int    // Port number remains Int
    // Sending a protected QUIC packet.
    suspend fun sendPacket(packet: ProtectedPayload): Boolean
    // Receiving raw bytes into a buffer.
>>>>>>> origin/jules_wip_8844705664950451013
    suspend fun receivePacket(buffer: ByteArray): Int
}
=======
// @Deprecated(...) expect interface QuicSocketContextValue ... { ... } // REMOVED
>>>>>>> origin/jules_wip_6906935130323988499

expect interface QuicProtectionContextValue : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicProtectionContextKey
<<<<<<< HEAD
    suspend fun protectPacket(context: CoroutineContext, packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ByteArray
    suspend fun unprotectPacket(context: CoroutineContext, packet: ByteArray, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket?
=======
    // Protects a QuicPacket and returns the serialized, protected bytes.
    fun protectPacket(packet: QuicPacket, keys: QuicInitialKeys, connection: QuicConnection): ProtectedPayload
    // Unprotects raw bytes into a QuicPacket.
    fun unprotectPacket(packet: ProtectedPayload, keys: QuicInitialKeys, connection: QuicConnection): QuicPacket?
>>>>>>> origin/jules_wip_8844705664950451013
}

expect interface QuicObservabilityContextValue : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = QuicObservabilityContextKey
    // ... methods remain unchanged
    fun recordHandshakeAttempt()
    fun recordHandshakeSuccess()
    fun recordHandshakeFailure()
    fun recordHandshakeError(error: Exception) // Standard exception type
    fun recordDataSendAttempt(size: Int) // Size is a primitive, clear enough
    fun recordDataSendSuccess(size: Int)
    fun recordDataSendError(error: Exception)
}

// Common context builder for simple keys
expect class SimpleQuicContextBuilder() {
    fun connection(context: QuicConnectionContextValue): SimpleQuicContextBuilder
    fun crypto(context: QuicCryptoContextValue): SimpleQuicContextBuilder
    // fun socket(context: QuicSocketContextValue): SimpleQuicContextBuilder // REMOVED
    fun protection(context: QuicProtectionContextValue): SimpleQuicContextBuilder
    fun observability(context: QuicObservabilityContextValue): SimpleQuicContextBuilder
    fun build(): CoroutineContext
}

<<<<<<< HEAD
<<<<<<< HEAD
// Define a placeholder for where host/port might come from for the target server.
// In a real app, this would be from config or method parameters.
// For these examples, let's assume there's some way to get targetHost/Port.
// This could be another CCEK element, or passed into these functions.
// For now, we'll hardcode or assume they are passed into performQuicHandshake.
=======
// Common extension functions using the simple keys
// These functions are not `expect` declarations, but their parameters and local variables
// would also need to be updated to reflect the changes in the expect interfaces if they were part
// of this refactoring task. For example:
// - `clientHelloPayload` in performQuicHandshake would be `ByteArray`.
// - `initialKeys` derived using `ConnectionID`.
// - `serializedPacket` would be `ProtectedPayload`.
// - `sendQuicData(data: ByteArray)` parameter would become `sendQuicData(data: StreamFrameData)`.
// The subtask is to only change expect declarations, so these are left as comments on impact.

suspend fun CoroutineContext.performQuicHandshake(): Boolean {
    val connectionContext = this[QuicConnectionContextKey] ?: error("QuicConnectionContext not found in CoroutineContext")
    val cryptoContext = this[QuicCryptoContextKey] ?: error("QuicCryptoContext not found in CoroutineContext")
    val socketContext = this[QuicSocketContextKey] ?: error("QuicSocketContext not found in CoroutineContext")
    val protectionContext = this[QuicProtectionContextKey] ?: error("QuicProtectionContext not found in CoroutineContext")
    val observabilityContext = this[QuicObservabilityContextKey] ?: error("QuicObservabilityContext not found in CoroutineContext")
>>>>>>> origin/jules_wip_8844705664950451013

suspend fun CoroutineContext.performQuicHandshake(targetHost: String, targetPort: Int): Boolean {
    val connectionContext = this[QuicConnectionContextKey] ?: error("QuicConnectionContext not found")
    val cryptoContext = this[QuicCryptoContextKey] ?: error("QuicCryptoContext not found")
    // val socketContext = this[QuicSocketContextKey] ?: error("QuicSocketContext not found") // OLD
    val protectionContext = this[QuicProtectionContextKey] ?: error("QuicProtectionContext not found")
    val observabilityContext = this[QuicObservabilityContextKey] ?: error("QuicObservabilityContext not found")
=======
suspend fun CoroutineContext.performQuicHandshake(targetHost: String, targetPort: Int): Boolean {
    val connectionContext = this[QuicConnectionContextKey] ?: error("QuicConnectionContext not found")
    val cryptoContext = this[QuicCryptoContextKey] ?: error("QuicCryptoContext not found")
    val protectionContext = this[QuicProtectionContextKey] ?: error("QuicProtectionContext not found")
    val observabilityContext = this[QuicObservabilityContextKey] ?: error("QuicObservabilityContext not found")

    val ioService = this[PlatformIoServiceKey] ?: error("PlatformIoService not found in CoroutineContext")
    val udpChannel = ioService.createUdpChannel() ?: run {
        observabilityContext.recordHandshakeError(Exception("Failed to create UDP channel"))
        return false
    }
>>>>>>> origin/jules_wip_6906935130323988499

    // NEW: Get PlatformIoService and create a channel
    val ioService = this[PlatformIoServiceKey] ?: error("PlatformIoService not found in CoroutineContext")
    val udpChannel = ioService.createUdpChannel() ?: run {
        observabilityContext.recordHandshakeError(Exception("Failed to create UDP channel"))
        return false
    }

    // Ensure channel is closed if handshake fails or completes
    try {
<<<<<<< HEAD
        observabilityContext.recordHandshakeAttempt()

<<<<<<< HEAD
        val clientHelloPayload = cryptoContext.generateClientHello(targetHost) // serverName = targetHost
=======
        val clientHelloPayload = cryptoContext.generateClientHello(targetHost)
>>>>>>> origin/jules_wip_6906935130323988499
=======
        // Assuming connectionContext.connection.connectionId is now ConnectionID
        // val clientHelloPayload = cryptoContext.generateClientHello( (connectionContext.connection.connectionId as ConnectionID), socketContext.host)
        val clientHelloPayload = cryptoContext.generateClientHello(socketContext.host) // Assuming serverName is host for now
>>>>>>> origin/jules_wip_8844705664950451013
        val initialPacket = connectionContext.createInitialPacket(clientHelloPayload)
        val initialKeys = cryptoContext.deriveInitialSecrets(this, connectionContext.connection.connectionId)
        val serializedPacket = protectionContext.protectPacket(this, initialPacket, initialKeys, connectionContext.connection)

<<<<<<< HEAD
        val targetAddress = SocketAddress(targetHost, targetPort)
<<<<<<< HEAD
        val bytesSent = udpChannel.send(serializedPacket, targetAddress) // NEW
        if (bytesSent <= 0) { // send returns bytes sent, or error code (usually via exception for suspend fun)
=======
        val bytesSent = udpChannel.send(serializedPacket, targetAddress)
        if (bytesSent <= 0) {
>>>>>>> origin/jules_wip_6906935130323988499
=======
        // Assuming connectionContext.connection.connectionId is ConnectionID
        val initialKeys = cryptoContext.deriveInitialSecrets(connectionContext.connection.connectionId as ConnectionID) // Cast for illustration

        val serializedPacket: ProtectedPayload = protectionContext.protectPacket(initialPacket, initialKeys, connectionContext.connection)

        val sendSuccess = socketContext.sendPacket(serializedPacket)
        if (!sendSuccess) {
>>>>>>> origin/jules_wip_8844705664950451013
            observabilityContext.recordHandshakeFailure()
            return false
        }

        val buffer = ByteArray(1500)
<<<<<<< HEAD
<<<<<<< HEAD
        // Assuming receive also needs a target address for some reason, or this is a general receive.
        // PlatformUdpChannel.receive does not take a target address.
        val (bytesReceived, sourceAddress) = udpChannel.receive(buffer) // NEW

        if (bytesReceived > 0) {
            val responseData = buffer.sliceArray(0 until bytesReceived)
            // Assuming parseQuicPacket is available and now takes context and keys
=======
        val (bytesReceived, sourceAddress) = udpChannel.receive(buffer)

        if (bytesReceived > 0) {
            val responseData = buffer.sliceArray(0 until bytesReceived)
>>>>>>> origin/jules_wip_6906935130323988499
            val parsedPacket = parseQuicPacket(this, responseData, connectionContext.connection, initialKeys) ?: run {
                 observabilityContext.recordHandshakeFailure()
                 return false
            }

<<<<<<< HEAD
            // Process server hello from parsedPacket.payload
=======
>>>>>>> origin/jules_wip_6906935130323988499
            val responseHandled = cryptoContext.processServerResponse(parsedPacket.payload)
=======
        val bytesReceived = socketContext.receivePacket(buffer)

        if (bytesReceived > 0) {
            val responseData: ProtectedPayload = ProtectedPayload(buffer.sliceArray(0 until bytesReceived))
            // The unprotectPacket function now expects ProtectedPayload
            // parseQuicPacket is not part of expect, its signature would also need update
            val parsedPacket = protectionContext.unprotectPacket(responseData, initialKeys, connectionContext.connection)


            // processServerResponse expects ByteArray, if it processes the raw protected payload, this is fine.
            // If it expects decrypted data, then parsedPacket.payload (or similar) should be passed.
            // For now, let's assume it processes the raw payload and internally handles unprotection if needed,
            // or that the type change implies it gets raw bytes.
            val responseHandled = cryptoContext.processServerResponse(responseData.data) // Passing raw bytes
>>>>>>> origin/jules_wip_8844705664950451013
            if (responseHandled) {
                connectionContext.transitionToEstablished()
                observabilityContext.recordHandshakeSuccess()
                // How is udpChannel propagated for future use?
                // For now, this function's scope is just the handshake.
                // One option: return a new context with the channel: this + udpChannel
                return true
            } else {
                observabilityContext.recordHandshakeFailure()
                return false
            }
        } else {
            observabilityContext.recordHandshakeFailure()
            return false
        }
    } catch (e: Exception) {
        observabilityContext.recordHandshakeError(e)
        return false
    } finally {
<<<<<<< HEAD
        udpChannel.close() // Ensure channel created for handshake is closed
    }
}

<<<<<<< HEAD
// For sendQuicData, assume PlatformUdpChannel is already in the context,
// established by some prior connection setup.
=======
        udpChannel.close()
    }
}

>>>>>>> origin/jules_wip_6906935130323988499
suspend fun CoroutineContext.sendQuicData(data: ByteArray, targetHost: String, targetPort: Int): Boolean {
    val connectionContext = this[QuicConnectionContextKey] ?: error("QuicConnectionContext not found")
    val cryptoContext = this[QuicCryptoContextKey] ?: error("QuicCryptoContext not found")
    val protectionContext = this[QuicProtectionContextKey] ?: error("QuicProtectionContext not found")
    val observabilityContext = this[QuicObservabilityContextKey] ?: error("QuicObservabilityContext not found")

<<<<<<< HEAD
    // NEW: Expect PlatformUdpChannel to be in the context for sending data
=======
>>>>>>> origin/jules_wip_6906935130323988499
    val udpChannel = this[PlatformUdpChannelKey] ?: error("PlatformUdpChannel not found in CoroutineContext for sendQuicData. Handshake should establish and add it.")
=======
// Changed `data: ByteArray` to `data: StreamFrameData`
suspend fun CoroutineContext.sendQuicData(data: StreamFrameData): Boolean {
    val connectionContext = this[QuicConnectionContextKey] ?: error("QuicConnectionContext not found in CoroutineContext")
    val cryptoContext = this[QuicCryptoContextKey] ?: error("QuicCryptoContext not found in CoroutineContext")
    val socketContext = this[QuicSocketContextKey] ?: error("QuicSocketContext not found in CoroutineContext")
    val protectionContext = this[QuicProtectionContextKey] ?: error("QuicProtectionContext not found in CoroutineContext")
    val observabilityContext = this[QuicObservabilityContextKey] ?: error("QuicObservabilityContext not found in CoroutineContext")
>>>>>>> origin/jules_wip_8844705664950451013

    observabilityContext.recordDataSendAttempt(data.payload.size) // Accessing .payload from StreamFrameData

    return try {
        val dataPacket = connectionContext.createDataPacket(data)
<<<<<<< HEAD
        // Placeholder for deriving current keys. In a real scenario, this would use established keys.
=======
>>>>>>> origin/jules_wip_6906935130323988499
        val currentKeys = cryptoContext.deriveInitialSecrets(this, connectionContext.connection.connectionId)
        val serializedPacket = protectionContext.protectPacket(this, dataPacket, currentKeys, connectionContext.connection)

<<<<<<< HEAD
        val targetAddress = SocketAddress(targetHost, targetPort)
<<<<<<< HEAD
        val bytesSent = udpChannel.send(serializedPacket, targetAddress) // NEW
=======
        val bytesSent = udpChannel.send(serializedPacket, targetAddress)
>>>>>>> origin/jules_wip_6906935130323988499

        if (bytesSent > 0) {
            observabilityContext.recordDataSendSuccess(data.size)
=======
        // Assuming connectionContext.connection.connectionId is ConnectionID
        val currentKeys = cryptoContext.deriveInitialSecrets(connectionContext.connection.connectionId as ConnectionID) // Cast for illustration

        val serializedPacket: ProtectedPayload = protectionContext.protectPacket(dataPacket, currentKeys, connectionContext.connection)

        val sendSuccess = socketContext.sendPacket(serializedPacket)
        if (sendSuccess) {
            observabilityContext.recordDataSendSuccess(data.payload.size)
>>>>>>> origin/jules_wip_8844705664950451013
            true
        } else {
            observabilityContext.recordDataSendError(RuntimeException("Failed to send data packet (bytesSent <= 0)"))
            false
        }
    } catch (e: Exception) {
        observabilityContext.recordDataSendError(e)
        false
    }
}

<<<<<<< HEAD
// QuicPacket, QuicInitialKeys, QuicConnection, parseQuicPacket are assumed accessible.
// (They are defined in evolution.QuicCurl.kt)
=======
// Placeholder for QuicConnection and QuicPacket as they are not defined in QuicSpecTypes.kt
// These would likely be defined elsewhere in the 'evolution' package.
expect class QuicConnection {
    val connectionId: Any // Should ideally be ConnectionID after full refactoring
}
expect class QuicPacket {
    // Define QuicPacket properties, e.g., header, payload
    val payload: ByteArray // Example property
}
// Placeholder for QuicInitialKeys
expect class QuicInitialKeys
>>>>>>> origin/jules_wip_8844705664950451013
