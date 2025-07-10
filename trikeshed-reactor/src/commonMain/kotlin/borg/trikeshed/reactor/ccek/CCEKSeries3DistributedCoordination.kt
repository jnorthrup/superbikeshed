@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.reactor.ccek

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.datetime.*
import borg.trikeshed.reactor.*
import borg.trikeshed.lib.*

/**
 * CCEK Series 3: Distributed Coordination
 * 
 * Implements distributed systems patterns like consensus, 
 * leader election, and distributed locking.
 */

// Distributed coordination layers
class ConsensusLayer(
    internal val nodes: List<CCEKChannelization>,
    internal val consensusAlgorithm: ConsensusAlgorithm = RaftConsensus()
) : DistributedLayer {
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is ConsensusOperation -> handleConsensus(operation) as T
            is LeaderElection -> handleLeaderElection(operation) as T
            else -> {
                // Route to leader if needed
                val leader = consensusAlgorithm.getLeader()
                leader.dispatch(operation)
            }
        }
    }
    
    internal suspend fun handleConsensus(operation: ConsensusOperation): ConsensusResult {
        val proposal = operation.proposal
        val startTime = Clock.System.now()
        
        // Phase 1: Prepare
        val prepareResponses = coroutineScope {
            nodes.map { node ->
                async {
                    try {
                        node.dispatch(PrepareRequest(proposal.id, proposal.value))
                    } catch (e: Exception) {
                        PrepareResponse(accepted = false, reason = e.message)
                    }
                }
            }.awaitAll()
        }
        
        val acceptCount = prepareResponses.count { it.accepted }
        if (acceptCount < nodes.size / 2 + 1) {
            return ConsensusResult(
                accepted = false,
                value = null,
                timestamp = Clock.System.now(),
                duration = Clock.System.now() - startTime
            )
        }
        
        // Phase 2: Accept
        val acceptResponses = coroutineScope {
            nodes.map { node ->
                async {
                    try {
                        node.dispatch(AcceptRequest(proposal.id, proposal.value))
                    } catch (e: Exception) {
                        AcceptResponse(accepted = false)
                    }
                }
            }.awaitAll()
        }
        
        val finalAcceptCount = acceptResponses.count { it.accepted }
        
        return ConsensusResult(
            accepted = finalAcceptCount >= nodes.size / 2 + 1,
            value = if (finalAcceptCount >= nodes.size / 2 + 1) proposal.value else null,
            timestamp = Clock.System.now(),
            duration = Clock.System.now() - startTime
        )
    }
    
    internal suspend fun handleLeaderElection(operation: LeaderElection): LeaderElectionResult {
        val result = consensusAlgorithm.electLeader(nodes)
        return LeaderElectionResult(
            leaderId = result.leaderId,
            term = result.term,
            timestamp = Clock.System.now()
        )
    }
    
    companion object : CoroutineContext.Key<ConsensusLayer>
}

class DistributedLockService(
    internal val coordinationService: CCEKChannelization,
    internal val lockTimeout: Duration = 30.seconds
) : DistributedLayer {
    
    internal val locks = mutableMapOf<String, DistributedLock>()
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is AcquireLock -> handleAcquireLock(operation) as T
            is ReleaseLock -> handleReleaseLock(operation) as T
            is CheckLock -> handleCheckLock(operation) as T
            else -> coordinationService.dispatch(operation)
        }
    }
    
    internal suspend fun handleAcquireLock(operation: AcquireLock): LockResult {
        val lockKey = operation.key
        val requestId = operation.requestId
        val timestamp = Clock.System.now()
        
        synchronized(locks) {
            val existingLock = locks[lockKey]
            
            if (existingLock != null && existingLock.isValid(timestamp)) {
                return LockResult(
                    acquired = false,
                    lockId = null,
                    owner = existingLock.owner,
                    expiresAt = existingLock.expiresAt
                )
            }
            
            // Lock is available or expired
            val newLock = DistributedLock(
                id = "${lockKey}_${timestamp.toEpochMilliseconds()}",
                key = lockKey,
                owner = requestId,
                acquiredAt = timestamp,
                expiresAt = timestamp + lockTimeout
            )
            
            locks[lockKey] = newLock
            
            return LockResult(
                acquired = true,
                lockId = newLock.id,
                owner = requestId,
                expiresAt = newLock.expiresAt
            )
        }
    }
    
    internal suspend fun handleReleaseLock(operation: ReleaseLock): LockResult {
        synchronized(locks) {
            val lock = locks[operation.key]
            
            if (lock?.owner == operation.requestId) {
                locks.remove(operation.key)
                return LockResult(
                    acquired = false,
                    lockId = null,
                    owner = null,
                    expiresAt = null
                )
            }
            
            return LockResult(
                acquired = false,
                lockId = lock?.id,
                owner = lock?.owner,
                expiresAt = lock?.expiresAt
            )
        }
    }
    
    internal suspend fun handleCheckLock(operation: CheckLock): LockResult {
        synchronized(locks) {
            val lock = locks[operation.key]
            val now = Clock.System.now()
            
            if (lock != null && lock.isValid(now)) {
                return LockResult(
                    acquired = lock.owner == operation.requestId,
                    lockId = lock.id,
                    owner = lock.owner,
                    expiresAt = lock.expiresAt
                )
            }
            
            return LockResult(
                acquired = false,
                lockId = null,
                owner = null,
                expiresAt = null
            )
        }
    }
    
    companion object : CoroutineContext.Key<DistributedLockService>
}

class EventualConsistencyLayer(
    internal val replicas: List<CCEKChannelization>,
    internal val conflictResolver: ConflictResolver = LastWriteWinsResolver()
) : DistributedLayer {
    
    internal val vectorClocks = mutableMapOf<String, VectorClock>()
    
    override suspend fun <T> dispatch(operation: ChannelOperation<T>): T {
        return when (operation) {
            is ReplicatedWrite -> handleReplicatedWrite(operation) as T
            is ReplicatedRead -> handleReplicatedRead(operation) as T
            else -> replicas.first().dispatch(operation)
        }
    }
    
    internal suspend fun handleReplicatedWrite(operation: ReplicatedWrite): ReplicationResult {
        val nodeId = operation.nodeId
        val timestamp = Clock.System.now()
        
        // Update vector clock
        val clock = vectorClocks.getOrPut(operation.key) { VectorClock() }
        clock.increment(nodeId)
        
        // Write to local replica first
        val localResult = replicas[0].dispatch(
            WriteOperation(operation.key, operation.value, clock.copy())
        )
        
        // Asynchronously replicate to other nodes
        GlobalScope.launch {
            replicas.drop(1).forEach { replica ->
                try {
                    replica.dispatch(
                        ReplicationUpdate(
                            key = operation.key,
                            value = operation.value,
                            vectorClock = clock.copy(),
                            sourceNode = nodeId
                        )
                    )
                } catch (e: Exception) {
                    // Log replication failure, will be handled by anti-entropy
                }
            }
        }
        
        return ReplicationResult(
            success = true,
            vectorClock = clock.copy(),
            timestamp = timestamp
        )
    }
    
    internal suspend fun handleReplicatedRead(operation: ReplicatedRead): ReadResult {
        val responses = coroutineScope {
            replicas.map { replica ->
                async {
                    try {
                        replica.dispatch(ReadOperation(operation.key))
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        
        // Resolve conflicts if multiple versions exist
        val resolved = if (responses.size > 1) {
            conflictResolver.resolve(responses)
        } else {
            responses.firstOrNull()
        }
        
        return ReadResult(
            value = resolved?.value,
            vectorClock = resolved?.vectorClock,
            timestamp = Clock.System.now()
        )
    }
    
    companion object : CoroutineContext.Key<EventualConsistencyLayer>
}

// Consensus algorithm abstraction
interface ConsensusAlgorithm {
    fun getLeader(): CCEKChannelization
    suspend fun electLeader(nodes: List<CCEKChannelization>): ElectionResult
}

class RaftConsensus : ConsensusAlgorithm {
    internal var currentLeader: CCEKChannelization? = null
    internal var currentTerm = 0L
    
    override fun getLeader(): CCEKChannelization {
        return currentLeader ?: throw NoLeaderException("No leader elected")
    }
    
    override suspend fun electLeader(nodes: List<CCEKChannelization>): ElectionResult {
        currentTerm++
        // Simplified Raft leader election
        currentLeader = nodes.first() // In real implementation, would vote
        
        return ElectionResult(
            leaderId = nodes.indexOf(currentLeader!!).toString(),
            term = currentTerm
        )
    }
}

// Vector clock for causality tracking
class VectorClock {
    internal val clocks = mutableMapOf<String, Long>()
    
    fun increment(nodeId: String) {
        clocks[nodeId] = (clocks[nodeId] ?: 0) + 1
    }
    
    fun merge(other: VectorClock) {
        other.clocks.forEach { (node, time) ->
            clocks[node] = maxOf(clocks[node] ?: 0, time)
        }
    }
    
    fun copy(): VectorClock = VectorClock().apply {
        clocks.putAll(this@VectorClock.clocks)
    }
    
    fun happensBefore(other: VectorClock): Boolean {
        return clocks.all { (node, time) ->
            time <= (other.clocks[node] ?: 0)
        } && clocks != other.clocks
    }
}

// Conflict resolution
interface ConflictResolver {
    fun resolve(values: List<VersionedValue>): VersionedValue
}

class LastWriteWinsResolver : ConflictResolver {
    override fun resolve(values: List<VersionedValue>): VersionedValue {
        return values.maxByOrNull { it.timestamp } ?: values.first()
    }
}

// Distributed operations
data class ConsensusOperation(
    val proposal: Proposal
) : ChannelOperation<ConsensusResult>

data class Proposal(
    val id: String,
    val value: Any
)

data class ConsensusResult(
    val accepted: Boolean,
    val value: Any?,
    val timestamp: Instant,
    val duration: Duration
)

data class PrepareRequest(
    val proposalId: String,
    val value: Any
) : ChannelOperation<PrepareResponse>

data class PrepareResponse(
    val accepted: Boolean,
    val reason: String? = null
)

data class AcceptRequest(
    val proposalId: String,
    val value: Any
) : ChannelOperation<AcceptResponse>

data class AcceptResponse(
    val accepted: Boolean
)

object LeaderElection : ChannelOperation<LeaderElectionResult>

data class LeaderElectionResult(
    val leaderId: String,
    val term: Long,
    val timestamp: Instant
)

data class ElectionResult(
    val leaderId: String,
    val term: Long
)

// Distributed lock operations
data class AcquireLock(
    val key: String,
    val requestId: String
) : ChannelOperation<LockResult>

data class ReleaseLock(
    val key: String,
    val requestId: String
) : ChannelOperation<LockResult>

data class CheckLock(
    val key: String,
    val requestId: String
) : ChannelOperation<LockResult>

data class LockResult(
    val acquired: Boolean,
    val lockId: String?,
    val owner: String?,
    val expiresAt: Instant?
)

data class DistributedLock(
    val id: String,
    val key: String,
    val owner: String,
    val acquiredAt: Instant,
    val expiresAt: Instant
) {
    fun isValid(now: Instant): Boolean = now < expiresAt
}

// Replication operations
data class ReplicatedWrite(
    val key: String,
    val value: Any,
    val nodeId: String
) : ChannelOperation<ReplicationResult>

data class ReplicatedRead(
    val key: String
) : ChannelOperation<ReadResult>

data class ReplicationResult(
    val success: Boolean,
    val vectorClock: VectorClock,
    val timestamp: Instant
)

data class WriteOperation(
    val key: String,
    val value: Any,
    val vectorClock: VectorClock
) : ChannelOperation<WriteResult>

data class WriteResult(
    val success: Boolean
)

data class ReadOperation(
    val key: String
) : ChannelOperation<VersionedValue>

data class ReadResult(
    val value: Any?,
    val vectorClock: VectorClock?,
    val timestamp: Instant
)

data class ReplicationUpdate(
    val key: String,
    val value: Any,
    val vectorClock: VectorClock,
    val sourceNode: String
) : ChannelOperation<Unit>

data class VersionedValue(
    val value: Any,
    val vectorClock: VectorClock,
    val timestamp: Instant = Clock.System.now()
)

// Exceptions
class NoLeaderException(message: String) : Exception(message)