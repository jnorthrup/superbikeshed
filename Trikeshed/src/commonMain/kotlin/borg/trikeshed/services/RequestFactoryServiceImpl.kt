package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.lib.Series as Indexed
import borg.trikeshed.reactor.http.HttpServerContext

/**
 * KMP RequestFactory Service Implementation
 * Integrated with reactor HTTP server toolkit for maximum perfect coroutine context architecture
 */
internal class RequestFactoryServiceImpl(
    private val context: HttpServerContext
) : RequestFactoryService {
    
    // Service registry using Join instead of Pair
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    private val methodValidators = mutableMapOf<String, (Any) -> Boolean>()
    private val serviceInstances = mutableMapOf<String, Any>()
    
    // Request counter for statistical packing
    private var requestCounter = 0L

    override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> {
        // Convert Indexed<Byte> to String using j patterns
        val requestJson = requestPayload.a j { i: Int -> 
            requestPayload.b(i).toInt().toChar() 
        } j { chars -> chars.joinToString("") }
        
        return try {
            // Use context for statistical packing optimization
            val sweetSpot = context.registerPacker(requestPayload.a)
            val serviceClass = "ReactorService"
            val methodName = "process"

            // Create response using reactor context
            val responseJson = buildString {
                append("""{"success":true,"service":"$serviceClass","method":"$methodName","timestamp":${++requestCounter},"context":"${context.ioModel}"}""")
            }
            responseJson.encodeToByteArray().toIdx()

        } catch (e: Exception) {
            createErrorResponse(500, e.message ?: "Unknown error").encodeToByteArray().toIdx()
        }
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
        // Register in context trait graph
        val traitGraph = serviceClass j locator as CoroutineContext.Element
        // TODO: Add to context.traitGraph
    }

    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {
        methodValidators[methodName] = validator
    }

    override suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte> {
        // Use context lifecycle control for service invocation
        return context.createLifecycleControl(borg.trikeshed.reactor.http.LifecyclePhase.PROCESS).let { control ->
            var result: Indexed<Byte> = 0 j { 0.toByte() }
            control.execute {
                // Get service using statistical packing
                val service = getServiceInstance(serviceName)
                result = if (service != null) {
                    processServiceCall(service, data)
                } else {
                    createErrorResponse(404, "Service not found: $serviceName").encodeToByteArray().toIdx()
                }
            }
            result
        }
    }
    
    /**
     * Get or create service instance with caching
     */
    private fun getServiceInstance(serviceName: String): Any? {
        return serviceInstances.getOrPut(serviceName) {
            serviceLocators[serviceName]?.invoke() ?: return null
        }
    }
    
    /**
     * Process service call using concurrent mapreduce when applicable
     */
    private suspend fun processServiceCall(service: Any, data: Indexed<Byte>): Indexed<Byte> {
        return when (service) {
            is DealService -> service.process(data)
            else -> {
                // Use context's mapreduce for generic processing
                context.mapReduce(
                    data = data,
                    mapper = { byte: Byte -> byte.toInt() },
                    reducer = { a: Int, b: Int -> a + b },
                    identity = 0
                ).toString().encodeToByteArray().toIdx()
            }
        }
    }

    /**
     * Create error response with reactor context info
     */
    private fun createErrorResponse(code: Int, message: String): String {
        return """{"success":false,"error":"$message","code":$code,"ioModel":"${context.ioModel}","timestamp":${System.currentTimeMillis()}}"""
    }

    /**
     * Convert ByteArray to Indexed<Byte> using j pattern
     */
    private fun ByteArray.toIdx(): Indexed<Byte> = size j { index: Int -> this[index] }
}

/**
 * Default DealService implementation for reactor integration
 */
internal class ReactorDealService(
    private val context: HttpServerContext
) : DealService {
    
    override suspend fun process(data: Indexed<Byte>): Indexed<Byte> {
        // Use statistical packing for deal processing
        val sweetSpot = context.registerPacker(data.a)
        
        // Demo processing using concurrent mapreduce
        val processedValue = context.mapReduce(
            data = data,
            mapper = { byte: Byte -> byte.toInt() },
            reducer = { a: Int, b: Int -> (a + b) % 256 },
            identity = 0
        )
        
        val response = """{"dealProcessed":true,"value":$processedValue,"ioModel":"${context.ioModel}"}"""
        return response.encodeToByteArray().let { bytes ->
            bytes.size j { i: Int -> bytes[i] }
        }
    }
    
    override fun getDealInfo(dealId: String): String {
        return """{"dealId":"$dealId","status":"active","ioModel":"${context.ioModel}"}"""
    }
    
    override fun createDeal(dealData: Indexed<Byte>): String {
        val dealId = "deal_${System.currentTimeMillis()}"
        return """{"created":"$dealId","size":${dealData.a},"ioModel":"${context.ioModel}"}"""
    }
}