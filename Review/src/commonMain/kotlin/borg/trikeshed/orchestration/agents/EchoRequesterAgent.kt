package borg.trikeshed.orchestration.agents

import borg.ipfs.IpfsPubSubService
import borg.trikeshed.orchestration.AgentMessage // Not directly used, but good for context
import borg.trikeshed.orchestration.AgentTopics
import borg.trikeshed.orchestration.BaseOrchestrationAgent
import borg.trikeshed.orchestration.GenericRequest
import borg.trikeshed.orchestration.GenericResponse
import borg.trikeshed.orchestration.ServiceAnnouncement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay // Not used directly, but often useful in agents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull // To wait for first non-null response
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlin.system.getTimeMillis

class EchoRequesterAgent(
    parentCoroutineContext: CoroutineContext,
    ipfsPubSubService: IpfsPubSubService,
    agentId: String? = null
) : BaseOrchestrationAgent(parentCoroutineContext, ipfsPubSubService, agentId ?: "agent-requester-${Random.nextInt(1000)}") {

    private val discoveredServices = MutableStateFlow<Map<String, ServiceAnnouncement>>(emptyMap())
    private var discoveryListenerJob: Job? = null

    override fun start() {
        println("EchoRequesterAgent $agentId starting.")
        discoveryListenerJob = launch {
            try {
                ipfsPubSubService.subscribe(AgentTopics.AGENT_DISCOVERY).collectLatest { messageJson ->
                    try {
                        val announcement = json.decodeFromString<ServiceAnnouncement>(messageJson)
                        // Add or update the service in the map
                        discoveredServices.value = discoveredServices.value + (announcement.serviceName to announcement)
                        println("Agent $agentId discovered/updated service: ${announcement.serviceName} from ${announcement.agentId} on topic ${announcement.serviceEndpointTopic}")
                    } catch (e: Exception) {
                        println("Agent $agentId failed to decode ServiceAnnouncement: $messageJson: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                 if (isActive) { // Check isActive before logging error from subscription
                    println("Agent $agentId error subscribing to ${AgentTopics.AGENT_DISCOVERY}: ${e.message}")
                }
            }
        }
        println("EchoRequesterAgent $agentId discovery listener started.")
    }

    suspend fun findFirstMatchingService(serviceName: String, timeoutMillis: Long = 5000): ServiceAnnouncement? {
        return withTimeoutOrNull(timeoutMillis) {
            discoveredServices.first { serviceMap -> serviceMap.containsKey(serviceName) }[serviceName]
        }
    }

    suspend fun sendEchoRequest(targetServiceName: String, message: String, timeoutMillis: Long = 10000): String? {
        println("Agent $agentId attempting to find service: $targetServiceName")
        val serviceInfo = findFirstMatchingService(targetServiceName, timeoutMillis / 3)
        if (serviceInfo == null) {
            println("Agent $agentId: Service $targetServiceName not found after timeout.")
            return null
        }
        println("Agent $agentId found service: ${serviceInfo.serviceName} on topic ${serviceInfo.serviceEndpointTopic}")

        val requestId = "req-${agentId}-${Random.nextInt(100000)}"
        // Unique response topic for this request
        val responseTopic = AgentTopics.getServiceResponseTopic(targetServiceName, requestId)

        val request = GenericRequest(
            agentId = agentId,
            timestamp = getTimeMillis(),
            requestId = requestId,
            targetService = targetServiceName,
            responseTopic = responseTopic,
            payload = message
        )

        var responseListenerJob: Job? = null
        val responseFlow = MutableStateFlow<GenericResponse?>(null)

        try {
            responseListenerJob = launch {
                 try {
                    println("Agent $agentId subscribing to response topic: $responseTopic")
                    ipfsPubSubService.subscribe(responseTopic).collectLatest { messageJson ->
                        try {
                            val genericResponse = json.decodeFromString<GenericResponse>(messageJson)
                            if (genericResponse.requestId == requestId) {
                                println("Agent $agentId received response for $requestId on $responseTopic: ${genericResponse.payload}")
                                responseFlow.value = genericResponse
                                // Once response is received, can cancel this specific listener
                                // This cancellation will happen in the finally block of the outer try
                            }
                        } catch (e: Exception) {
                            println("Agent $agentId failed to decode GenericResponse on $responseTopic: $messageJson: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                         println("Agent $agentId error subscribing to response topic $responseTopic: ${e.message}")
                    }
                } finally {
                    println("Agent $agentId: Listener for $responseTopic is ending.")
                }
            }

            publishMessage(serviceInfo.serviceEndpointTopic, request)
            println("Agent $agentId sent request $requestId to ${serviceInfo.serviceEndpointTopic} for service ${serviceInfo.serviceName}")

            // Wait for the response
            val receivedResponse = withTimeoutOrNull(timeoutMillis * 2 / 3) {
                responseFlow.filterNotNull().first() // Wait for the first non-null response
            }

            if (receivedResponse == null) {
                println("Agent $agentId: Timed out waiting for response for request $requestId")
                return null
            }

            return if (receivedResponse.error == null) {
                receivedResponse.payload
            } else {
                println("Agent $agentId received error in response for $requestId: ${receivedResponse.error}")
                "Error: ${receivedResponse.error}"
            }
        } finally {
            println("Agent $agentId: Cancelling response listener job for topic $responseTopic.")
            responseListenerJob?.cancel()
        }
    }

    override fun stop() {
        discoveryListenerJob?.cancel()
        super.stop()
        println("EchoRequesterAgent $agentId fully stopped.")
    }
}
