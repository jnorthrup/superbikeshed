package borg.trikeshed.services

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.PlatformServiceInvoker
import borg.trikeshed.lib.j
import borg.trikeshed.lib.toIdx

/**
 * Core types and protocols for the RequestFactory system.
 * This broker handles the translation between JSON wire format and type-safe Kotlin objects.
 */
class RequestFactoryBroker(private val serviceInvoker: PlatformServiceInvoker) {
    
    data class EntityVersion(
        val id: EntityProxyId,
        val version: Long
    )

    data class EntityDelta(
        val id: EntityProxyId,
        val changes: Map<String, Any?>
    )

    // Client-side functionality
    class Client(private val transport: Transport) {
        private val pendingRequests = mutableMapOf<String, (Response) -> Unit>()
        private val entityVersions = mutableMapOf<String, Long>()

        suspend fun invoke(serviceToken: String, methodToken: String, args: Indexed<Any?>): Response {
            val request = Request.Invoke(serviceToken, methodToken, args)
            return sendRequest(request)
        }

        private suspend fun sendRequest(request: Request): Response {
            // Simplified implementation
            return Response.Success("Request sent")
        }
    }

    // Server-side functionality
    class Server(private val serviceInvoker: PlatformServiceInvoker) {
        private val services = mutableMapOf<String, Any>()
        
        fun registerService(token: String, service: Any) {
            services[token] = service
        }

        suspend fun handleRequest(requestBytes: Indexed<Byte>): Indexed<Byte> {
            // Simplified implementation
            val response = Response.Success("Request handled")
            return "OK".encodeToByteArray().toList().toIdx()
        }
    }

    // Transport interface for client-server communication
    interface Transport {
        suspend fun send(data: Indexed<Byte>)
        suspend fun receive(): Indexed<Byte>
    }

    // Request types
    sealed class Request {
        abstract val serviceToken: String
        abstract val methodToken: String
        
        data class Invoke(
            override val serviceToken: String,
            override val methodToken: String,
            val args: Indexed<Any?>
        ) : Request()

        data class Create(
            override val serviceToken: String,
            override val methodToken: String,
            val entityToken: String,
            val initialState: Map<String, Any?>
        ) : Request()

        data class Update(
            override val serviceToken: String,
            override val methodToken: String,
            val entityToken: String,
            val delta: EntityDelta,
            val version: EntityVersion
        ) : Request()

        data class Delete(
            override val serviceToken: String,
            override val methodToken: String,
            val entityToken: String,
            val version: EntityVersion
        ) : Request()
    }

    // Response types
    sealed class Response {
        data class Success(val result: Any?) : Response()
        data class Failure(val error: String) : Response()
        data class EntityCreated(
            val entityToken: String,
            val id: EntityProxyId,
            val version: Long
        ) : Response()
        data class EntityUpdated(
            val entityToken: String,
            val version: Long
        ) : Response()
        data class EntityDeleted(
            val entityToken: String
        ) : Response()
    }

    // Helper functions for request/response parsing
    private fun parseRequest(json: Any?): Request {
        val obj = json as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        
        val serviceToken = getStringField(obj, "serviceToken")
        val methodToken = getStringField(obj, "methodToken")
        
        return when (val type = getStringField(obj, "type")) {
            "invoke" -> Request.Invoke(
                serviceToken,
                methodToken,
                getArrayField(obj, "args").size j { i:Int -> getArrayField(obj, "args")[i] }
            )
            "create" -> Request.Create(
                serviceToken,
                methodToken,
                getStringField(obj, "entityToken"),
                getObjectField(obj, "initialState")
            )
            "update" -> Request.Update(
                serviceToken,
                methodToken,
                getStringField(obj, "entityToken"),
                EntityDelta(
                    getStringField(obj, "entityId"),
                    getObjectField(obj, "changes")
                ),
                EntityVersion(
                    getStringField(obj, "entityId"),
                    getLongField(obj, "version")
                )
            )
            "delete" -> Request.Delete(
                serviceToken,
                methodToken,
                getStringField(obj, "entityToken"),
                EntityVersion(
                    getStringField(obj, "entityId"),
                    getLongField(obj, "version")
                )
            )
            else -> throw IllegalArgumentException("Unknown request type: $type")
        }
    }

    private fun parseResponse(json: Any?): Response {
        val obj = json as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        
        return when (val type = getStringField(obj, "type")) {
            "success" -> Response.Success(obj["result"])
            "failure" -> Response.Failure(getStringField(obj, "error"))
            "entityCreated" -> Response.EntityCreated(
                getStringField(obj, "entityToken"),
                getStringField(obj, "id"),
                getLongField(obj, "version")
            )
            "entityUpdated" -> Response.EntityUpdated(
                getStringField(obj, "entityToken"),
                getLongField(obj, "version")
            )
            "entityDeleted" -> Response.EntityDeleted(
                getStringField(obj, "entityToken")
            )
            else -> throw IllegalArgumentException("Unknown response type: $type")
        }
    }

    private fun serializeRequest(request: Request): Indexed<Indexed<String, Any?>> {
        val base = 2 j { i ->
            when (i) {
                0 -> "serviceToken" j request.serviceToken
                1 -> "methodToken" j request.methodToken
                else -> "" j null
            }
        }
        
        return when (request) {
            is Request.Invoke -> 3 j { i ->
                when (i) {
                    0 -> "serviceToken" j request.serviceToken
                    1 -> "methodToken" j request.methodToken
                    2 -> "type" j "invoke"
                    else -> "" j null
                }
            }
            is Request.Create -> 4 j { i ->
                when (i) {
                    0 -> "serviceToken" j request.serviceToken
                    1 -> "methodToken" j request.methodToken
                    2 -> "type" j "create"
                    3 -> "entityToken" j request.entityToken
                    else -> "" j null
                }
            }
            is Request.Update -> 6 j { i ->
                when (i) {
                    0 -> "serviceToken" j request.serviceToken
                    1 -> "methodToken" j request.methodToken
                    2 -> "type" j "update"
                    3 -> "entityToken" j request.entityToken
                    4 -> "entityId" j request.delta.id
                    5 -> "version" j request.version.version
                    else -> "" j null
                }
            }
            is Request.Delete -> 5 j { i ->
                when (i) {
                    0 -> "serviceToken" j request.serviceToken
                    1 -> "methodToken" j request.methodToken
                    2 -> "type" j "delete"
                    3 -> "entityToken" j request.entityToken
                    4 -> "entityId" j request.version.id
                    else -> "" j null
                }
            }
        }
    }

    private fun serializeResponse(response: Response): Indexed<Indexed<String, Any?>> {
        return when (response) {
            is Response.Success -> 2 j { i ->
                when (i) {
                    0 -> "type" j "success"
                    1 -> "result" j response.result
                    else -> "" j null
                }
            }
            is Response.Failure -> 2 j { i ->
                when (i) {
                    0 -> "type" j "failure"
                    1 -> "error" j response.error
                    else -> "" j null
                }
            }
            is Response.EntityCreated -> 4 j { i ->
                when (i) {
                    0 -> "type" j "entityCreated"
                    1 -> "entityToken" j response.entityToken
                    2 -> "id" j response.id
                    3 -> "version" j response.version
                    else -> "" j null
                }
            }
            is Response.EntityUpdated -> 3 j { i ->
                when (i) {
                    0 -> "type" j "entityUpdated"
                    1 -> "entityToken" j response.entityToken
                    2 -> "version" j response.version
                    else -> "" j null
                }
            }
            is Response.EntityDeleted -> 2 j { i ->
                when (i) {
                    0 -> "type" j "entityDeleted"
                    1 -> "entityToken" j response.entityToken
                    else -> "" j null
                }
            }
        }
    }

    // Helper functions for JSON field access
    private fun getStringField(map: Map<*, *>, field: String): String {
        return map[field] as? String ?: throw IllegalArgumentException("Invalid string field: $field")
    }
    
    private fun getLongField(map: Map<*, *>, field: String): Long {
        return map[field] as? Long ?: throw IllegalArgumentException("Invalid long field: $field")
    }
    
    private fun getArrayField(map: Map<*, *>, field: String): List<*> {
        return map[field] as? List<*> ?: throw IllegalArgumentException("Invalid array field: $field")
    }
    
    private fun getObjectField(map: Map<*, *>, field: String): Map<String, Any?> {
        return map[field] as? Map<String, Any?> ?: throw IllegalArgumentException("Invalid object field: $field")
    }
}

expect fun parse(data: ByteArray): Any
expect fun stringify(data: Any): String
expect suspend fun invokeService(serviceName: String, data: ByteArray): ByteArray