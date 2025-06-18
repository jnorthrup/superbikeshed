package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import borg.trikeshed.couch.*

/**
 * CouchDB-backed implementation of RequestFactoryService.
 * Adapts RequestFactory protocol to CouchDB operations.
 */
class CouchRequestFactoryService(
    private val couchClient: CouchClient,
    private val defaultDatabase: DatabaseName = DatabaseName("entities")
) : RequestFactoryService {
    // Entity version tracking
    private val entityVersions = mutableMapOf<String, Long>()
    
    // Service locators for entity types
    private val serviceLocators = mutableMapOf<String, () -> Any>()
    
    // Method validators
    private val methodValidators = mutableMapOf<String, (Series<Any?>) -> Boolean>()

    override suspend fun process(payload: Series<Byte>): Series<Byte> {
        val requestJson = payload.▶.toByteArray().decodeToString()
        val request = JsonImpl.parse(requestJson)
        
        return try {
            val response = when (val type = (request as? Map<*, *>)?.get("type")?.toString()) {
                "batch" -> handleBatch(request)
                else -> handleSingleRequest(request)
            }
            JsonImpl.stringify(response).encodeToByteArray().toSeries()
        } catch (e: Exception) {
            val error = RequestFactoryBroker.Response.Failure(e.message ?: "Unknown error")
            JsonImpl.stringify(error).encodeToByteArray().toSeries()
        }
    }

    private suspend fun handleBatch(request: Map<*, *>): List<RequestFactoryBroker.Response> {
        val requests = request["requests"] as? List<*>
            ?: throw IllegalArgumentException("Batch request must contain 'requests' array")

        // Group requests by type for optimal batch processing
        val creates = mutableListOf<RequestFactoryBroker.Request.Create>()
        val updates = mutableListOf<RequestFactoryBroker.Request.Update>()
        val deletes = mutableListOf<RequestFactoryBroker.Request.Delete>()
        val invokes = mutableListOf<RequestFactoryBroker.Request.Invoke>()

        // Parse and group requests
        requests.forEach { req ->
            when (val parsed = parseRequest(req)) {
                is RequestFactoryBroker.Request.Create -> creates.add(parsed)
                is RequestFactoryBroker.Request.Update -> updates.add(parsed)
                is RequestFactoryBroker.Request.Delete -> deletes.add(parsed)
                is RequestFactoryBroker.Request.Invoke -> invokes.add(parsed)
            }
        }

        // Process each group in optimal order
        val results = mutableListOf<RequestFactoryBroker.Response>()

        // 1. Process creates in bulk
        if (creates.isNotEmpty()) {
            results.addAll(handleBulkCreate(creates))
        }

        // 2. Process updates in bulk
        if (updates.isNotEmpty()) {
            results.addAll(handleBulkUpdate(updates))
        }

        // 3. Process deletes in bulk
        if (deletes.isNotEmpty()) {
            results.addAll(handleBulkDelete(deletes))
        }

        // 4. Process invokes (these can't be batched but can run in parallel)
        if (invokes.isNotEmpty()) {
            results.addAll(invokes.map { handleInvoke(it) })
        }

        return results
    }

    private suspend fun handleBulkCreate(requests: List<RequestFactoryBroker.Request.Create>): List<RequestFactoryBroker.Response> {
        // Convert requests to CouchDB documents
        val docs = requests.map { request ->
            CouchDocument(
                _id = DocumentId(request.entityToken),
                data = request.initialState
            )
        }

        // Perform bulk create
        val responses = couchClient.bulkDocs(defaultDatabase, Series(docs))

        // Process responses
        return responses.▶.mapIndexed { index, response ->
            if (response.ok) {
                val entityId = RequestFactoryBroker.EntityProxyId(response.id.value)
                entityVersions[entityId.value] = response.rev.value.toLong()
                RequestFactoryBroker.Response.EntityCreated(
                    entityToken = requests[index].entityToken,
                    id = entityId,
                    version = response.rev.value.toLong()
                )
            } else {
                RequestFactoryBroker.Response.Failure("Failed to create entity: ${requests[index].entityToken}")
            }
        }
    }

    private suspend fun handleBulkUpdate(requests: List<RequestFactoryBroker.Request.Update>): List<RequestFactoryBroker.Response> {
        // First verify all versions
        requests.forEach { request ->
            val currentVersion = entityVersions[request.version.id.value]
            if (currentVersion == null || currentVersion != request.version.version) {
                throw IllegalStateException("Version mismatch for entity: ${request.entityToken}")
            }
        }

        // Get all documents in one batch
        val docIds = requests.map { DocumentId(it.entityToken) }
        val existingDocs = docIds.map { id ->
            couchClient.getDocument(defaultDatabase, id)
        }

        // Apply updates
        val updatedDocs = existingDocs.zip(requests) { doc, request ->
            doc.copy(_id = doc._id, _rev = doc._rev, data = doc.data + request.delta.changes)
        }

        // Perform bulk update
        val responses = couchClient.bulkDocs(defaultDatabase, Series(updatedDocs))

        // Process responses
        return responses.▶.mapIndexed { index, response ->
            if (response.ok) {
                entityVersions[requests[index].version.id.value] = response.rev.value.toLong()
                RequestFactoryBroker.Response.EntityUpdated(
                    entityToken = requests[index].entityToken,
                    version = response.rev.value.toLong()
                )
            } else {
                RequestFactoryBroker.Response.Failure("Failed to update entity: ${requests[index].entityToken}")
            }
        }
    }

    private suspend fun handleBulkDelete(requests: List<RequestFactoryBroker.Request.Delete>): List<RequestFactoryBroker.Response> {
        // First verify all versions
        requests.forEach { request ->
            val currentVersion = entityVersions[request.version.id.value]
            if (currentVersion == null || currentVersion != request.version.version) {
                throw IllegalStateException("Version mismatch for entity: ${request.entityToken}")
            }
        }

        // Prepare delete documents (CouchDB requires _deleted flag)
        val deleteRequests = requests.map { request ->
            CouchDocument(
                _id = DocumentId(request.entityToken),
                _rev = RevisionId(request.version.version.toString()),
                data = mapOf("_deleted" to true)
            )
        }

        // Perform bulk delete
        val responses = couchClient.bulkDocs(defaultDatabase, Series(deleteRequests))

        // Process responses
        return responses.▶.mapIndexed { index, response ->
            if (response.ok) {
                entityVersions.remove(requests[index].version.id.value)
                RequestFactoryBroker.Response.EntityDeleted(requests[index].entityToken)
            } else {
                RequestFactoryBroker.Response.Failure("Failed to delete entity: ${requests[index].entityToken}")
            }
        }
    }

    private suspend fun handleSingleRequest(request: Any?): RequestFactoryBroker.Response {
        return when (val parsedRequest = parseRequest(request)) {
            is RequestFactoryBroker.Request.Invoke -> handleInvoke(parsedRequest)
            is RequestFactoryBroker.Request.Create -> handleCreate(parsedRequest)
            is RequestFactoryBroker.Request.Update -> handleUpdate(parsedRequest)
            is RequestFactoryBroker.Request.Delete -> handleDelete(parsedRequest)
        }
    }

    private suspend fun handleCreate(request: RequestFactoryBroker.Request.Create): RequestFactoryBroker.Response {
        val doc = CouchDocument(
            _id = DocumentId(request.entityToken),
            data = request.initialState
        )
        
        val result = couchClient.createDocument(defaultDatabase, doc)
        val entityId = RequestFactoryBroker.EntityProxyId(result.id.value)
        entityVersions[entityId.value] = result.rev.value.toLong()
        
        return RequestFactoryBroker.Response.EntityCreated(
            entityToken = request.entityToken,
            id = entityId,
            version = result.rev.value.toLong()
        )
    }

    private suspend fun handleUpdate(request: RequestFactoryBroker.Request.Update): RequestFactoryBroker.Response {
        // Verify version
        val currentVersion = entityVersions[request.version.id.value]
            ?: return RequestFactoryBroker.Response.Failure("Entity not found")
        
        if (currentVersion != request.version.version) {
            return RequestFactoryBroker.Response.Failure("Version mismatch")
        }

        // Get existing document
        val existingDoc = couchClient.getDocument(
            defaultDatabase,
            DocumentId(request.entityToken)
        )

        // Apply changes
        val updatedDoc = existingDoc.copy(
            _id = existingDoc._id,
            _rev = existingDoc._rev,
            data = existingDoc.data + request.delta.changes
        )

        // Save updated document
        val result = couchClient.updateDocument(defaultDatabase, updatedDoc)
        entityVersions[request.version.id.value] = result.rev.value.toLong()

        return RequestFactoryBroker.Response.EntityUpdated(
            entityToken = request.entityToken,
            version = result.rev.value.toLong()
        )
    }

    private suspend fun handleDelete(request: RequestFactoryBroker.Request.Delete): RequestFactoryBroker.Response {
        val currentVersion = entityVersions[request.version.id.value]
            ?: return RequestFactoryBroker.Response.Failure("Entity not found")

        if (currentVersion != request.version.version) {
            return RequestFactoryBroker.Response.Failure("Version mismatch")
        }

        couchClient.deleteDocument(
            defaultDatabase,
            DocumentId(request.entityToken),
            RevisionId(currentVersion.toString())
        )

        entityVersions.remove(request.version.id.value)
        return RequestFactoryBroker.Response.EntityDeleted(request.entityToken)
    }

    private suspend fun handleInvoke(request: RequestFactoryBroker.Request.Invoke): RequestFactoryBroker.Response {
        val service = serviceLocators[request.serviceToken.value]?.invoke()
            ?: return RequestFactoryBroker.Response.Failure("Service not found")

        val validator = methodValidators[request.methodToken.value]
        if (validator != null && !validator(request.args)) {
            return RequestFactoryBroker.Response.Failure("Invalid method arguments")
        }

        // Execute method using reflection
        val method = service::class.members.find { it.name == request.methodToken.value }
            ?: return RequestFactoryBroker.Response.Failure("Method not found")

        val result = method.call(service, *request.args.▶.toList().toTypedArray())
        return RequestFactoryBroker.Response.Success(result)
    }

    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }

    override fun registerMethodValidator(methodName: String, validator: (Series<Any?>) -> Boolean) {
        methodValidators[methodName] = validator
    }

    private fun parseRequest(json: Any?): RequestFactoryBroker.Request {
        val obj = json as? Map<*, *> ?: throw IllegalArgumentException("Expected JSON object")
        val serviceToken = RequestFactoryBroker.ServiceToken(getStringField(obj, "serviceToken"))
        val methodToken = RequestFactoryBroker.MethodToken(getStringField(obj, "methodToken"))
        
        return when (val type = getStringField(obj, "type")) {
            "invoke" -> RequestFactoryBroker.Request.Invoke(
                serviceToken = serviceToken,
                methodToken = methodToken,
                args = parseArgs(obj["args"])
            )
            "create" -> RequestFactoryBroker.Request.Create(
                serviceToken = serviceToken,
                methodToken = methodToken,
                entityToken = getStringField(obj, "entityToken"),
                initialState = parseState(obj["initialState"])
            )
            "update" -> RequestFactoryBroker.Request.Update(
                serviceToken = serviceToken,
                methodToken = methodToken,
                entityToken = getStringField(obj, "entityToken"),
                delta = parseDelta(obj["delta"]),
                version = parseVersion(obj["version"])
            )
            "delete" -> RequestFactoryBroker.Request.Delete(
                serviceToken = serviceToken,
                methodToken = methodToken,
                entityToken = getStringField(obj, "entityToken"),
                version = parseVersion(obj["version"])
            )
            else -> throw IllegalArgumentException("Unknown request type: $type")
        }
    }

    private fun parseArgs(json: Any?): Series<Any?> {
        val list = json as? List<*> ?: return Series(emptyList())
        return Series(list)
    }

    private fun parseState(json: Any?): Map<String, Any?> {
        return (json as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
    }

    private fun parseDelta(json: Any?): RequestFactoryBroker.EntityDelta {
        val obj = json as? Map<*, *> ?: throw IllegalArgumentException("Expected delta object")
        return RequestFactoryBroker.EntityDelta(
            id = RequestFactoryBroker.EntityProxyId(getStringField(obj, "id")),
            changes = parseState(obj["changes"])
        )
    }

    private fun parseVersion(json: Any?): RequestFactoryBroker.EntityVersion {
        val obj = json as? Map<*, *> ?: throw IllegalArgumentException("Expected version object")
        return RequestFactoryBroker.EntityVersion(
            id = RequestFactoryBroker.EntityProxyId(getStringField(obj, "id")),
            version = (obj["version"] as? Number)?.toLong()
                ?: throw IllegalArgumentException("Invalid version number")
        )
    }

    private fun getStringField(obj: Map<*, *>, field: String): String {
        return obj[field]?.toString() ?: throw IllegalArgumentException("Missing field: $field")
    }
} 