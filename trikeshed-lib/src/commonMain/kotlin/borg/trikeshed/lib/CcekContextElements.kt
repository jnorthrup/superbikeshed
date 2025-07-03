import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Indexed
data class SocksIngressChannel(val id: String)
data class SocksEgressChannel(val id: String)

// === Execution Control Elements ===

data class ExecutionId(val id: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ExecutionId>
}

data class ExecutionPhase(val phase: String) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ExecutionPhase>
}

// === Knowledge & Schema Elements ===

data class TransformationRule(val name: String)
data class Constraint(val description: String)

data class TransformationRules(val rules: Indexed<TransformationRule>) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<TransformationRules>
}

data class ValidationConstraints(val constraints: Indexed<Constraint>) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ValidationConstraints>
}

// === I/O & Protocol Elements ===

/**
 * Specifies the desired IO backend capability for an operation.
 * Replaces the need for a complex IOContextManager.
 */
enum class IoCapability { URING, KQUEUE, NIO, EPOLL, POSIX_FD }
data class IoPreference(val capability: IoCapability) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<IoPreference>
}

/**
 * Carries protocol-specific channels directly in the context.
 * This is the refined version of SocksIngress/Egress channels.
 */
data class ProtocolChannels(
    val ingress: SocksIngressChannel,
    val egress: SocksEgressChannel
) : CoroutineContext.Element {
    override val key = Key
    companion object Key : CoroutineContext.Key<ProtocolChannels>
}

// === Easy-Access Extensions ===

val CoroutineContext.executionId: String? get() = this[ExecutionId]?.id
val CoroutineContext.phase: String? get() = this[ExecutionPhase]?.phase
val CoroutineContext.rules: Indexed<TransformationRule>? get() = this[TransformationRules]?.rules
val CoroutineContext.ioCapability: IoCapability? get() = this[IoPreference]?.capability
val CoroutineContext.protocolChannels: ProtocolChannels? get() = this[ProtocolChannels]