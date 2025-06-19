package borg.trikeshed.services

import borg.trikeshed.services.*
import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import borg.trikeshed.nio.platformCurrentTimeMillis

/**
 * Core types and protocols for the RequestFactory system.
 * This broker handles the translation between JSON wire format and type-safe Kotlin objects.
 */
object RequestFactoryBroker {
    
    
    

    data class EntityVersion(
        val id: EntityProxyId,
        val version: Long
    )

    data class EntityDelta(
        val id: EntityProxyId,
        val changes: Map<String, Any?>
    )

    sealed interface Request {
        val serviceToken: ServiceToken
        val methodToken: MethodToken

        data class Invoke(
            override val serviceToken: ServiceToken,
            override val methodToken: MethodToken,
            val args: Series<Any?>
        ) : Request

        data class Create(
            override val serviceToken: ServiceToken,
            override val methodToken: MethodToken,
            val entityToken: String,
            val initialState: Map<String, Any?>
        ) : Request

        data class Update(
            override val serviceToken: ServiceToken,
            override val methodToken: MethodToken,
            val entityToken: String,
            val delta: EntityDelta,
            val version: EntityVersion
        ) : Request

        data class Delete(
            override val serviceToken: ServiceToken,
            override val methodToken: MethodToken,
            val entityToken: String,
            val version: EntityVersion
        ) : Request
    }

    sealed interface Response {
        data class Success(val result: Any?) : Response
        data class Failure(val error: String) : Response
        data class EntityCreated(
            val entityToken: String,
            val id: EntityProxyId,
            val version: Long
        ) : Response
        data class EntityUpdated(
            val entityToken: String,
            val version: Long
        ) : Response
        data class EntityDeleted(
            val entityToken: String
        ) : Response
    }

    // Client-side functionality
    class Client(private val transport: Transport) {
        private val entityVersions = mutableMapOf<EntityProxyId, Long>()
        private val pendingRequests = mutableMapOf<MethodToken, (Response) -> Unit>()

        suspend fun invoke(
            serviceToken: String,
            methodToken: String,
            args: Series<Any?>,
            callback: (Response) -> Unit
        ) {
            val request = Request.Invoke(
                serviceToken,
                methodToken,
                args
            )
            sendRequest(request, callback)
        }

        suspend fun createEntity(
            serviceToken: String,
            entityToken: String,
            initialState: Map<String, Any?>,
            callback: (Response) -> Unit
        ) {
            val request = Request.Create(
                serviceToken,
                "create",
                entityToken,
                initialState
            )
            sendRequest(request, callback)
        }

        suspend fun updateEntity(
            serviceToken: String,
            entityToken: String,
            id: EntityProxyId,
            changes: Map<String, Any?>,
            callback: (Response) -> Unit
        ) {
            val version = entityVersions[id] ?: throw IllegalStateException("Unknown entity: $id")
            val request = Request.Update(
                serviceToken,
                "update",
                entityToken,
                EntityDelta(id, changes),
                EntityVersion(id, version)
            )
            sendRequest(request, callback)
        }

        suspend fun deleteEntity(
            serviceToken: String,
            entityToken: String,
            id: EntityProxyId,
            callback: (Response) -> Unit
        ) {
            val version = entityVersions[id] ?: throw IllegalStateException("Unknown entity: $id")
            val request = Request.Delete(
                serviceToken,
                "delete",
                entityToken,
                EntityVersion(id, version)
            )
            sendRequest(request, callback)
        }

        private suspend fun sendRequest(request: Request, callback: (Response) -> Unit) {
            pendingRequests[request.methodToken] = callback
            val json = serializeRequest(request)
            transport.send(json.play.toByteArray().toSeries())
        }

        suspend fun handleResponse(responseBytes: Series<Byte>) {
            val responseJson = responseBytes.play.toByteArray().decodeToString()
            val response = JsonImpl.parse(responseJson)
            
            val methodToken = getStringField(response, "methodToken")
            val callback = pendingRequests.remove(methodToken) ?: return

            when (val result = parseResponse(response)) {
                is Response.EntityCreated -> {
                    entityVersions[result.id] = result.version
                    callback(result)
                }
                is Response.EntityUpdated -> {
                    val id = getStringField(response, "entityId")
                    entityVersions[id] = result.version
                    callback(result)
                }
                else -> callback(result)
            }
        }
    }

    // Server-side functionality
    class Server(private val serviceInvoker: PlatformServiceInvoker) {
        private val services = mutableMapOf<ServiceToken, Any>()
        
        private val entityVersions = mutableMapOf<EntityProxyId, Long>()
        private val validators = mutableMapOf<MethodToken, (Series<Any?>) -> Boolean>()

        fun registerService(token: String, service: Any) {
            services[token] = service
        }

        fun registerValidator(methodToken: String, validator: (Series<Any?>) -> Boolean) {
            validators[methodToken] = validator
        }

        suspend fun handleRequest(requestBytes: Series<Byte>): Series<Byte> {
            val requestJson = requestBytes.play.toByteArray().decodeToString()
            val request = JsonImpl.parse(requestJson)
            
            val response = try {
                when (val parsedRequest = parseRequest(request)) {
                    is Request.Invoke -> handleInvoke(parsedRequest)
                    is Request.Create -> handleCreate(parsedRequest)
                    is Request.Update -> handleUpdate(parsedRequest)
                    is Request.Delete -> handleDelete(parsedRequest)
                }
            } catch (e: Exception) {
                Response.Failure(e.message ?: "Unknown error")
            }

            val json = serializeResponse(response)
            return JsonImpl.stringify(json).encodeToByteArray().toSeries()
        }

        private fun handleInvoke(request: Request.Invoke): Response {
            val service = services[request.serviceToken] ?: return Response.Failure("Service not found")
            
            // Validate if required
            validators[request.methodToken]?.let { validator ->
                if (!validator(request.args)) {
                    return Response.Failure("Validation failed")
                }
            }

            return try {
                val result = serviceInvoker.invokeService(
                    service,
                    request.methodToken,
                    *request.args.play.toList().toTypedArray()
                )
                Response.Success(result)
            } catch (e: Exception) {
                Response.Failure(e.message ?: "Invocation failed")
            }
        }

        private fun handleCreate(request: Request.Create): Response {
            val service = services[request.serviceToken] ?: return Response.Failure("Service not found")
            
            val id = generateEntityId()
            entityVersions[id] = 1L

            return Response.EntityCreated(request.entityToken, id, 1L)
        }

        private fun handleUpdate(request: Request.Update): Response {
            val currentVersion = entityVersions[request.version.id]
                ?: return Response.Failure("Entity not found")

            if (currentVersion != request.version.version) {
                return Response.Failure("Version mismatch")
            }

            entityVersions[request.version.id] = currentVersion + 1
            return Response.EntityUpdated(request.entityToken, currentVersion + 1)
        }

        private fun handleDelete(request: Request.Delete): Response {
            val currentVersion = entityVersions[request.version.id]
                ?: return Response.Failure("Entity not found")

            if (currentVersion != request.version.version) {
                return Response.Failure("Version mismatch")
            }

            entityVersions.remove(request.version.id)
            return Response.EntityDeleted(request.entityToken)
        }
    }

    // Transport interface for client-server communication
    interface Transport {
        suspend fun send(data: Series<Byte>)
        suspend fun receive(): Series<Byte>
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

    private fun serializeRequest(request: Request): Map<String, Any?> {
        val base = mapOf(
            "serviceToken" to request.serviceToken,
            "methodToken" to request.methodToken
        )
        
        return when (request) {
            is Request.Invoke -> base + mapOf(
                "type" to "invoke",
                "args" to request.args.play.toList()
            )
            is Request.Create -> base + mapOf(
                "type" to "create",
                "entityToken" to request.entityToken,
                "initialState" to request.initialState
            )
            is Request.Update -> base + mapOf(
                "type" to "update",
                "entityToken" to request.entityToken,
                "entityId" to request.delta.id,
                "changes" to request.delta.changes,
                "version" to request.version.version
            )
            is Request.Delete -> base + mapOf(
                "type" to "delete",
                "entityToken" to request.entityToken,
                "entityId" to request.version.id,
                "version" to request.version.version
            )
        }
    }

    private fun serializeResponse(response: Response): Map<String, Any?> {
        return when (response) {
            is Response.Success -> mapOf(
                "type" to "success",
                "result" to response.result
            )
            is Response.Failure -> mapOf(
                "type" to "failure",
                "error" to response.error
            )
            is Response.EntityCreated -> mapOf(
                "type" to "entityCreated",
                "entityToken" to response.entityToken,
                "id" to response.id,
                "version" to response.version
            )
            is Response.EntityUpdated -> mapOf(
                "type" to "entityUpdated",
                "entityToken" to response.entityToken,
                "version" to response.version
            )
            is Response.EntityDeleted -> mapOf(
                "type" to "entityDeleted",
                "entityToken" to response.entityToken
            )
        }
    }

    // Helper functions for JSON field access
    private fun getStringField(obj: Any?, field: String): String {
        val map = obj as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        return map[field]?.toString() ?: throw IllegalArgumentException("Missing field: $field")
    }

    private fun getLongField(obj: Any?, field: String): Long {
        val map = obj as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        return map[field]?.toString()?.toLongOrNull() ?: throw IllegalArgumentException("Invalid long field: $field")
    }

    private fun getArrayField(obj: Any?, field: String): List<*> {
        val map = obj as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        return map[field] as? List<*> ?: throw IllegalArgumentException("Invalid array field: $field")
    }

    private fun getObjectField(obj: Any?, field: String): Map<String, Any?> {
        val map = obj as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        @Suppress("UNCHECKED_CAST")
        return map[field] as? Map<String, Any?> ?: throw IllegalArgumentException("Invalid object field: $field")
    }

    private fun generateEntityId(): String = 
        platformCurrentTimeMillis().toString(16) + (0..0xFFFF).random().toString(16)
}