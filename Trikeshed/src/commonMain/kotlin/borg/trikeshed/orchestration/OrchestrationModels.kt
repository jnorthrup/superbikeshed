package borg.trikeshed.orchestration

import kotlinx.serialization.Serializable

object AgentTopics {
    const val AGENT_DISCOVERY = "/trikeshed/agents/discovery"
    const val AGENT_HEARTBEAT = "/trikeshed/agents/heartbeat"
    // Example service-specific topic pattern:
    fun getServiceRequestTopic(serviceName: String) = "/trikeshed/service/$serviceName/requests"
    fun getServiceResponseTopic(serviceName: String, requestId: String) = "/trikeshed/service/$serviceName/responses/$requestId"
}

@Serializable
sealed class AgentMessage {
    abstract val agentId: String
    abstract val timestamp: Long // Unix timestamp ms
}

@Serializable
data class ServiceAnnouncement(
    override val agentId: String,
    override val timestamp: Long,
    val serviceName: String,
    val serviceEndpointTopic: String // The topic this service listens on for requests
) : AgentMessage()

@Serializable
data class Heartbeat(
    override val agentId: String,
    override val timestamp: Long,
    val status: String = "ALIVE"
) : AgentMessage()

@Serializable
data class GenericRequest(
    override val agentId: String, // ID of the requester
    override val timestamp: Long,
    val requestId: String, // Unique ID for this request
    val targetService: String, // Name of the target service
    val responseTopic: String, // Topic to send the response to
    val payload: String // Could be JSON string for more complex requests
) : AgentMessage()

@Serializable
data class GenericResponse(
    override val agentId: String, // ID of the responder
    override val timestamp: Long,
    val requestId: String, // Correlates to the GenericRequest
    val payload: String, // Could be JSON string
    val error: String? = null
) : AgentMessage() 