package fiduciary.concentric

import borg.trikeshed.lib.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Represents a work stealing mechanism within the concentric task network.
 * This class manages local work queues and implements algorithms for
 * stealing work from neighboring agents.
 *
 * @param agentId The NUID of the current agent.
 * @param quicProtocol The QUIC concentric protocol instance for communication.
 * @param groupManager The group manager for understanding network topology.
 */
class WorkStealing(
    private val agentId: NUID,
    private val quicProtocol: QuicConcentricProtocol,
    private val groupManager: GroupTemplates
) {

    // Local work queue for tasks assigned to this agent
    private val localWorkQueue: ConcurrentLinkedDeque<TaskShard> = ConcurrentLinkedDeque()

    // Counter for tasks stolen from other agents
    private val stolenTaskCount: AtomicInteger = AtomicInteger(0)

    // Configuration for work stealing
    private val config = WorkStealingConfig()

    /**
     * Adds a task to the local work queue.
     * @param task The task shard to add.
     */
    fun addTask(task: TaskShard) {
        localWorkQueue.addLast(task)
    }

    /**
     * Retrieves a task from the local work queue.
     * @return The next task shard, or null if the queue is empty.
     */
    fun getNextTask(): TaskShard? {
        return localWorkQueue.pollFirst()
    }

    /**
     * Initiates the work stealing process. This coroutine will
     * periodically attempt to steal work from other agents.
     */
    fun startWorkStealing(scope: CoroutineScope) {
        scope.launch {
            while (isActive) {
                delay(config.stealingInterval)
                attemptToStealWork()
            }
        }
    }

    /**
     * Attempts to steal work from a randomly selected neighbor.
     */
    private suspend fun attemptToStealWork() {
        if (localWorkQueue.isEmpty()) {
            val potentialNeighbors = groupManager.getNeighbors(agentId)
            if (potentialNeighbors.isNotEmpty()) {
                val targetAgent = potentialNeighbors.random(Random.Default)
                val stolenTask = requestWorkFromNeighbor(targetAgent)
                if (stolenTask != null) {
                    localWorkQueue.addFirst(stolenTask)
                    stolenTaskCount.incrementAndGet()
                    println("Agent $agentId stole a task from $targetAgent. Total stolen: ${stolenTaskCount.get()}")
                } else {
                    println("Agent $agentId failed to steal from $targetAgent.")
                }
            } else {
                println("Agent $agentId has no neighbors to steal from.")
            }
        }
    }

    /**
     * Requests work from a specific neighboring agent using the QUIC protocol.
     * Implements exponential backoff for failed requests.
     *
     * @param targetAgent The NUID of the agent to steal from.
     * @return The stolen task shard, or null if stealing failed.
     */
    private suspend fun requestWorkFromNeighbor(targetAgent: NUID): TaskShard? {
        var attempts = 0
        var delayTime = config.initialBackoffDelay

        while (attempts < config.maxStealingAttempts) {
            try {
                val response = quicProtocol.sendWorkStealingRequest(agentId, targetAgent, config.requestAmount)
                if (response.isNotEmpty()) {
                    // Assuming the response contains a single task for simplicity
                    return response.first()
                }
            } catch (e: Exception) {
                println("Work stealing attempt $attempts to $targetAgent failed: ${e.message}")
            }

            attempts++
            delay(delayTime)
            delayTime = (delayTime * config.backoffMultiplier).coerceAtMost(config.maxBackoffDelay)
        }
        return null
    }

    /**
     * Handles an incoming work stealing request from another agent.
     *
     * @param requestingAgent The NUID of the agent requesting work.
     * @param amount The amount of work requested.
     * @return A list of task shards to be given to the requesting agent.
     */
    fun handleWorkStealingRequest(requestingAgent: NUID, amount: Int): List<TaskShard> {
        val tasksToSteal = mutableListOf<TaskShard>()
        repeat(amount) {
            localWorkQueue.pollLast()?.let { tasksToSteal.add(it) }
        }
        println("Agent $agentId provided ${tasksToSteal.size} tasks to $requestingAgent.")
        return tasksToSteal
    }

    /**
     * Configuration for the work stealing algorithm.
     */
    data class WorkStealingConfig(
        val stealingInterval: Duration = 1000.milliseconds, // How often to attempt stealing
        val requestAmount: Int = 1, // How many tasks to request at once
        val maxStealingAttempts: Int = 5, // Max attempts to steal from one neighbor
        val initialBackoffDelay: Duration = 100.milliseconds, // Initial delay for exponential backoff
        val backoffMultiplier: Double = 2.0, // Multiplier for exponential backoff
        val maxBackoffDelay: Duration = 5000.milliseconds // Max delay for exponential backoff
    )

    /**
     * Returns the current number of tasks in the local work queue.
     */
    fun getLocalWorkQueueSize(): Int {
        return localWorkQueue.size
    }

    /**
     * Returns the total number of tasks stolen by this agent.
     */
    fun getTotalStolenTasks(): Int {
        return stolenTaskCount.get()
    }
}
