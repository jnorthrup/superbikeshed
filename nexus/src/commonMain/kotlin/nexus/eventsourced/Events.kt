package nexus.eventsourced

import borg.trikeshed.lib.*
import kotlinx.datetime.Instant

/**
 * Event-sourced architecture for Nexus
 * 
 * Core principles:
 * - All state changes are represented as events
 * - Events are immutable and append-only
 * - Current state is derived from event replay
 * - CQRS separation of commands and queries
 */

// Base event interface
sealed interface Event {
    val id: EventId
    val timestamp: Instant
    val version: Long
    val aggregateId: AggregateId
}

// Value types
@JvmInline
value class EventId(val value: String)

@JvmInline
value class AggregateId(val value: String)

@JvmInline
value class CommandId(val value: String)

@JvmInline
value class ProjectionId(val value: String)

// Domain events
sealed class DomainEvent : Event {
    // Session events
    data class SessionStarted(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val userId: String,
        val platform: String
    ) : DomainEvent()
    
    data class SessionEnded(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val reason: String
    ) : DomainEvent()
    
    // Tool events
    data class ToolRegistered(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val toolId: String,
        val name: String,
        val description: String,
        val capabilities: Indexed<String>
    ) : DomainEvent()
    
    data class ToolExecuted(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val toolId: String,
        val input: String,
        val output: String,
        val duration: Long,
        val success: Boolean
    ) : DomainEvent()
    
    data class ToolComposed(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val composedToolId: String,
        val sourceTools: Indexed<String>,
        val compositionType: CompositionType
    ) : DomainEvent()
    
    // Interaction events
    data class UserMessageReceived(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val message: String,
        val context: Map<String, String>
    ) : DomainEvent()
    
    data class SystemResponseGenerated(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val response: String,
        val confidence: Double
    ) : DomainEvent()
    
    data class ErrorOccurred(
        override val id: EventId,
        override val timestamp: Instant,
        override val version: Long,
        override val aggregateId: AggregateId,
        val errorType: String,
        val message: String,
        val stackTrace: String?
    ) : DomainEvent()
}

// Composition types
enum class CompositionType {
    SEQUENTIAL,
    PARALLEL,
    CHOICE,
    CONDITIONAL
}

// Commands
sealed class Command {
    abstract val id: CommandId
    abstract val aggregateId: AggregateId
    
    data class StartSession(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val userId: String,
        val platform: String
    ) : Command()
    
    data class EndSession(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val reason: String
    ) : Command()
    
    data class RegisterTool(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val toolId: String,
        val name: String,
        val description: String,
        val capabilities: Indexed<String>
    ) : Command()
    
    data class ExecuteTool(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val toolId: String,
        val input: String
    ) : Command()
    
    data class ComposeTool(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val sourceTools: Indexed<String>,
        val compositionType: CompositionType,
        val name: String
    ) : Command()
    
    data class ProcessMessage(
        override val id: CommandId,
        override val aggregateId: AggregateId,
        val message: String,
        val context: Map<String, String>
    ) : Command()
}

// Event store interface
interface EventStore {
    suspend fun append(event: Event): Result<Unit, EventStoreError>
    suspend fun load(aggregateId: AggregateId, fromVersion: Long = 0): Result<Indexed<Event>, EventStoreError>
    suspend fun loadAll(fromVersion: Long = 0): Result<Indexed<Event>, EventStoreError>
    suspend fun getLatestVersion(aggregateId: AggregateId): Result<Long, EventStoreError>
}

// Event store errors
sealed class EventStoreError {
    data class ConcurrencyConflict(val expectedVersion: Long, val actualVersion: Long) : EventStoreError()
    data class AggregateNotFound(val aggregateId: AggregateId) : EventStoreError()
    data class StorageError(val message: String) : EventStoreError()
}

// Event metadata
data class EventMetadata(
    val causationId: CommandId,
    val correlationId: String,
    val userId: String?,
    val source: String
)

// Event envelope for storage
data class EventEnvelope(
    val event: Event,
    val metadata: EventMetadata,
    val position: Long
)

// Event handler interface
interface EventHandler<E : Event> {
    suspend fun handle(event: E)
}

// Event bus for publishing
interface EventBus {
    suspend fun publish(event: Event)
    fun <E : Event> subscribe(eventType: Class<E>, handler: EventHandler<E>)
    fun <E : Event> unsubscribe(eventType: Class<E>, handler: EventHandler<E>)
}

// Snapshot support
data class Snapshot(
    val aggregateId: AggregateId,
    val version: Long,
    val timestamp: Instant,
    val state: Any
)

interface SnapshotStore {
    suspend fun save(snapshot: Snapshot): Result<Unit, EventStoreError>
    suspend fun load(aggregateId: AggregateId): Result<Snapshot?, EventStoreError>
}

// Event sourcing utilities
object EventSourcingUtils {
    fun generateEventId(): EventId = EventId("evt-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}")
    fun generateCommandId(): CommandId = CommandId("cmd-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}")
    fun generateAggregateId(type: String): AggregateId = AggregateId("$type-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}")
}

// Event serialization
interface EventSerializer {
    fun serialize(event: Event): String
    fun deserialize(data: String): Event
}

// JSON event serializer (simplified)
class JsonEventSerializer : EventSerializer {
    override fun serialize(event: Event): String {
        // In production, use proper JSON serialization
        return when (event) {
            is DomainEvent.SessionStarted -> """
                {"type":"SessionStarted","id":"${event.id.value}","timestamp":"${event.timestamp}",
                 "version":${event.version},"aggregateId":"${event.aggregateId.value}",
                 "userId":"${event.userId}","platform":"${event.platform}"}
            """.trimIndent()
            else -> throw UnsupportedOperationException("Serialization not implemented for ${event::class}")
        }
    }
    
    override fun deserialize(data: String): Event {
        // In production, use proper JSON deserialization
        throw UnsupportedOperationException("Deserialization not implemented")
    }
}