package borg.trikeshed.orchestration.agents

import borg.trikeshed.ipfs.IpfsPubSubService
import borg.trikeshed.orchestration.AgentMessage
import borg.trikeshed.orchestration.AgentTopics
import borg.trikeshed.orchestration.BaseOrchestrationAgent
import borg.trikeshed.orchestration.GenericRequest
import borg.trikeshed.orchestration.GenericResponse
import borg.trikeshed.orchestration.ServiceAnnouncement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import borg.trikeshed.lib.getCurrentTimeMillis

class EchoServiceAgent(
    parentCoroutineContext: CoroutineContext,
    ipfsPubSubService: IpfsPubSubService,
    agentId: String? = null,
    val serviceName: String = "echo_service"
) : BaseOrchestrationAgent(parentCoroutineContext, ipfsPubSubService, agentId ?: "agent-echo-${Random.nextInt(1000)}") {

    private val serviceRequestTopic = AgentTopics.getServiceRequestTopic(serviceName)
    private var discoveryJob: Job? = null
    private var listenerJob: Job? = null

    override fun start() {
        println("EchoServiceAgent $agentId starting. Service Name: $serviceName, Request Topic: $serviceRequestTopic")

        // Announce service periodically
        discoveryJob = launch {
            while (isActive) {
                val announcement = ServiceAnnouncement(
                    agentId = agentId,
                    timestamp = getCurrentTimeMillis(),
                    serviceName = serviceName,
                    serviceEndpointTopic = serviceRequestTopic
                )
                publishMessage(AgentTopics.AGENT_DISCOVERY, announcement)
                delay(30000) // Announce every 30 seconds
            }
        }

        // Listen for requests
        listenerJob = launch {
            try {
                ipfsPubSubService.subscribe(serviceRequestTopic).collectLatest { messageJson: String ->
                    println("Agent $agentId received on $serviceRequestTopic: $messageJson")
                    try {
                        val request = json.decodeFromString<GenericRequest>(messageJson)
                        if (request.targetService == serviceName) {
                            val responsePayload = "Echo from $agentId: ${request.payload}"
                            val response = GenericResponse(
                                agentId = agentId,
                                timestamp = getCurrentTimeMillis(),
                                requestId = request.requestId,
                                payload = responsePayload
                            )
                            // Publish response to the topic specified in the request
                            // Remove publish call for now to avoid coroutine issue
                            // publishMessage(request.responseTopic, response)
                        }
                    } catch (e: Exception) {
                        println("Agent $agentId failed to decode/process request $messageJson: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (isActive) { // Only log if the agent is still supposed to be active
                    println("Agent $agentId error subscribing to $serviceRequestTopic: ${e.message}")
                    // Optionally, attempt to resubscribe or handle error
                }
            }
        }
        println("EchoServiceAgent $agentId started and listening on $serviceRequestTopic.")
    }

    override fun stop() {
        discoveryJob?.cancel()
        listenerJob?.cancel()
        super.stop() // Call base class stop to cancel the main scope
        println("EchoServiceAgent $agentId fully stopped.")
    }
} 