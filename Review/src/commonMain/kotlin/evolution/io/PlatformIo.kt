package evolution.io // New package for IO CCEK services

import kotlin.coroutines.CoroutineContext

// --- CCEK Keys for I/O Services ---

object PlatformIoServiceKey : CoroutineContext.Key<PlatformIoService>
object PlatformUdpChannelKey : CoroutineContext.Key<PlatformUdpChannel> // If individual channels are context elements

// --- Interest Operations for Selector ---

enum class InterestOp {
    READ,
    WRITE
    // ACCEPT, CONNECT for TCP if ever needed
}

// --- Socket Address (Common Definition) ---
// SocketAddress is now replaced by borg.trikeshed.io.network.NetworkAddress (typealias to Join<String, Int>)
// No expect class needed here anymore for SocketAddress.
// Usages below will be updated.
// Placeholder import for the new NetworkAddress type.
import borg.trikeshed.io.network.NetworkAddress // This is Join<String, Int>

// --- Selection Event from Selector Loop ---
// channelKey: Could be platform's native key (e.g., java.nio.channels.SelectionKey)
// attachment: Optional data attached during registration
data class SelectionEvent(
    val channelKey: Any?,
    val interestOp: InterestOp,
    val attachment: Any?
)

// --- Platform Independent UDP Channel Interface ---

expect interface PlatformUdpChannel : CoroutineContext.Element { // Can also be a simple interface if not a context element itself
    suspend fun bind(localAddress: NetworkAddress): Boolean // Changed SocketAddress to NetworkAddress
    fun getLocalAddress(): NetworkAddress? // Changed SocketAddress to NetworkAddress
    override val key: CoroutineContext.Key<*> // Typically PlatformUdpChannelKey if it is a context element

    /**
     * Registers this channel with the platform's selector mechanism, managed by a PlatformIoService.
     * @param interest The interest operation (READ, WRITE).
     * @param attachment Optional data to be returned with SelectionEvent.
     * @return A platform-specific key representing the registration, or null if registration failed.
     *         This key will be part of the SelectionEvent.
     */
    suspend fun register(interest: InterestOp, attachment: Any? = null): Any?
                                        // Should ideally take selector instance or get from context.
                                        // For now, assumes channel knows its selector or can find it.

    suspend fun send(data: ByteArray, targetAddress: NetworkAddress): Int // Changed SocketAddress to NetworkAddress
    suspend fun receive(buffer: ByteArray): borg.trikeshed.lib.Join<Int, NetworkAddress?> // Bytes read, source address - Changed Pair to Join

    fun configureBlocking(block: Boolean) // May throw if called after registration on some platforms
    fun close()

    /**
     * Gets the underlying native selector key associated with this channel's registration, if any.
     * This is useful for correlating SelectionEvent.channelKey back to a PlatformUdpChannel instance
     * if the event only provides the raw native key.
     */
    fun getNativeKey(): Any?
}

// --- Platform I/O Service Interface (Selector and Channel Factory) ---

expect interface PlatformIoService : CoroutineContext.Element {
    fun close() // Method to close the service and release resources
    override val key: CoroutineContext.Key<*> get() = PlatformIoServiceKey

    /**
     * Creates a new platform-specific UDP channel.
     * The channel is initially not registered with the selector.
     */
    suspend fun createUdpChannel(): PlatformUdpChannel?

    /**
     * Runs the selector loop. This function will typically suspend until the selector is stopped
     * or an error occurs. It will invoke the provided handler for each I/O readiness event.
     * @param handler A suspend function that processes SelectionEvent.
     */
    suspend fun runSelectorLoop(handler: suspend (SelectionEvent) -> Unit)

    /**
     * Wakes up a selector loop that might be blocking in a select operation.
     * Useful for gracefully shutting down the selector loop from another coroutine.
     */
    fun wakeupSelector() // Added for controlling the selector loop
}
