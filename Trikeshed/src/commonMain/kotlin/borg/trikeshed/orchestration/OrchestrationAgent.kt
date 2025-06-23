package borg.trikeshed.orchestration

import borg.trikeshed.ipfs.IpfsPubSubService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random // For default agent ID

interface OrchestrationAgent : CoroutineScope {
    val agentId: String
    val ipfsPubSubService: IpfsPubSubService

    fun start()
    fun stop()
}

abstract class BaseOrchestrationAgent(
    parentCoroutineContext: CoroutineContext,
    override val ipfsPubSubService: IpfsPubSubService,
    givenAgentId: String? = null
) : OrchestrationAgent {

    // Ensure the context has a Job for cancellation
    override val coroutineContext: CoroutineContext = parentCoroutineContext + SupervisorJob() + Job()

    override val agentId: String = givenAgentId ?: "agent-${Random.nextInt(10000, 99999)}"

    protected val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    abstract override fun start()

    override fun stop() {
        coroutineContext.cancelChildren() // Cancel children jobs of this scope
        coroutineContext.cancel()         // Cancel the scope itself
        println("Agent $agentId stopped.")
    }

    protected suspend inline fun <reified T : AgentMessage> publishMessage(topic: String, message: T) {
        val messageJson = json.encodeToString(message)
        ipfsPubSubService.publish(topic, messageJson)
        println("Agent $agentId published to $topic: $messageJson")
    }
} 