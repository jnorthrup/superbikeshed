package borg.trikeshed.logging

import borg.trikeshed.nio.slabs.MemorySlab
import borg.trikeshed.nio.slabs.MemorySlabManagerService
import kotlin.coroutines.CoroutineContext // For constructor, though not actively used yet

/**
 * A utility class that demonstrates writing serialized [SimpleEvent] instances to [MemorySlab]s.
 * It acquires slabs from a [MemorySlabManagerService] and handles basic slab rollover when
 * the current slab becomes full. This class is primarily for demonstration and testing purposes.
 *
 * Implements [AutoCloseable] for managing the lifecycle of its resources, particularly the active slab.
 *
 * @param slabManager The [MemorySlabManagerService] used to acquire [MemorySlab] instances.
 * @param context The [CoroutineContext] which might be used to obtain other services or for structured concurrency
 *                in more advanced scenarios (currently unused).
 */
class SlabEventWriter(
    private val slabManager: MemorySlabManagerService,
    @Suppress("UNUSED_PARAMETER") private val context: CoroutineContext // Keep for future use, suppress warning for now
) : AutoCloseable {
    private var activeSlab: MemorySlab? = null
    private var slabCount = 0 // Just for diagnostics or simple tracking

    // Example for managing multiple slabs if needed:
    // private val allSlabs: MutableList<MemorySlab> = mutableListOf()

    init {
        acquireNewSlab()
    }

    private fun acquireNewSlab() {
        // In a more complex scenario, a filled 'activeSlab' might be added to a list of 'allSlabs' here.
        // The current 'activeSlab' is simply replaced. The 'releaseSlab' call's utility depends on
        // whether the slabManager implements pooling. If not pooling, this might be a no-op.
        // activeSlab?.let { slabManager.releaseSlab(it) } // Example if old slab should be released to a pool

        activeSlab = slabManager.acquireSlab() // Get a fresh slab from the manager
        // allSlabs.add(activeSlab!!) // If managing a list of all slabs
        slabCount++
        // Example logging: println("SlabEventWriter: Acquired new slab (total: $slabCount), capacity: ${activeSlab?.capacity}")
    }

    /**
     * Serializes the given [SimpleEvent] and writes it to the active [MemorySlab].
     * If the current slab does not have enough remaining capacity to hold the serialized event,
     * a new slab is acquired before writing.
     *
     * @param event The [SimpleEvent] to log.
     */
    fun logEvent(event: SimpleEvent) {
        val serializedEvent = event.serialize()
        val eventSize = serializedEvent.size

        if (activeSlab == null) { // Should ideally be initialized in constructor
            acquireNewSlab()
        }

        // Check if current slab has enough space; if not, acquire a new one.
        if (activeSlab!!.remaining < eventSize) {
            acquireNewSlab()
        }

        activeSlab!!.putBytes(serializedEvent, 0, eventSize)
    }

    /**
     * Convenience function to create a [SimpleEvent] from parameters and log it.
     * @see logEvent
     * @param timestamp The event timestamp.
     * @param type The event type.
     * @param payload The event payload.
     */
    fun logEvent(timestamp: Long, type: Int, payload: ByteArray) {
        logEvent(SimpleEvent(timestamp, type, payload))
    }

    /**
     * Reads and deserializes all [SimpleEvent]s from the *currently active* [MemorySlab].
     * This method is primarily for demonstration and testing purposes. It does not handle
     * reading from previously filled and archived slabs.
     *
     * @return A list of [SimpleEvent]s deserialized from the current slab. Returns an empty list
     *         if the active slab is null or contains no valid event data.
     */
    fun readEventsFromCurrentSlab(): List<SimpleEvent> {
        val events = mutableListOf<SimpleEvent>()
        activeSlab?.let { slab ->
            val bufferToRead = slab.getRawByteBuffer()
            while (bufferToRead.hasRemaining()) {
                val event = SimpleEvent.deserialize(bufferToRead)
                if (event != null) {
                    events.add(event)
                } else {
                    // Stop if deserialization fails (e.g., partial write, corruption, or end of valid data)
                    break
                }
            }
        }
        return events
    }

    /**
     * Returns the current active [MemorySlab] for external processing or inspection.
     * In a more sophisticated system, this might return a list of all filled slabs
     * that are pending archival or further processing. This simplified version only provides the current one.
     *
     * @return A list containing the current active [MemorySlab], or an empty list if no slab is active.
     */
    fun getAllSlabsForExternalProcessing(): List<MemorySlab> {
        return activeSlab?.let { listOf(it) } ?: emptyList()
    }

    /**
     * Closes the event writer. Currently, this primarily nullifies the reference to the active slab.
     * Depending on the [MemorySlabManagerService]'s pooling strategy, releasing the active slab
     * back to the manager might be appropriate here, but it's commented out as the default
     * behavior is to rely on GC for non-pooled, GC-managed slabs.
     */
    override fun close() {
        // The decision to call slabManager.releaseSlab(it) depends on the manager's strategy.
        // If slabs are pooled, it's important. If they are GC-managed and not pooled,
        // it might be a no-op or only for bookkeeping in the manager.
        // activeSlab?.let { slabManager.releaseSlab(it) }
        activeSlab = null
    }
}
