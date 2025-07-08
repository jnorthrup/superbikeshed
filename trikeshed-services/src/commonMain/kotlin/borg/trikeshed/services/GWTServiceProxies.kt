@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

/**
 * GWT-style Service Proxies for Type-safe RPC
 * 
 * Implements GWT RequestFactory patterns:
 * - Type-safe service proxies
 * - Entity versioning and change tracking
 * - Request batching
 * - Service locators
 * - Method validation
 */
class GWTServiceProxies(
    internal val requestFactory: EvolvedRequestFactory,
    internal val entityRegistry: EntityRegistry = EntityRegistry()
) {
    
    // Service proxy registry
    internal val serviceProxies = mutableMapOf<KClass<*>, ServiceProxy<*>>()
    
    // Entity proxy registry
    internal val entityProxies = mutableMapOf<String, EntityProxy<*>>()
    
    // Request context for batching
    internal val requestContexts = mutableMapOf<String, RequestContext>()
    
    // Service locators
    internal val serviceLocators = mutableMapOf<String, () -> Any>()

    /**
     * Create a type-safe service proxy
     */
    inline fun <reified T : Any> createServiceProxy(): ServiceProxy<T> {
        val serviceClass = T::class
        return serviceProxies.getOrPut(serviceClass) {
            ServiceProxy<T>(serviceClass, requestFactory, entityRegistry)
        } as ServiceProxy<T>
    }

    /**
     * Create an entity proxy for versioned entities
     */
    inline fun <reified T : Any> createEntityProxy(entityId: String): EntityProxy<T> {
        val entityClass = T::class
        val proxyKey = "${entityClass.simpleName}:$entityId"
        
        return entityProxies.getOrPut(proxyKey) {
            EntityProxy<T>(entityId, entityClass, requestFactory, entityRegistry)
        } as EntityProxy<T>
    }

    /**
     * Create a request context for batching operations
     */
    fun createRequestContext(): RequestContext {
        val contextId = generateContextId()
        val context = RequestContext(contextId, requestFactory, entityRegistry)
        requestContexts[contextId] = context
        return context
    }

    /**
     * Register a service locator (GWT pattern)
     */
    fun registerServiceLocator(serviceClass: String, locator: () -> Any) {
        serviceLocators[serviceClass] = locator
    }

    /**
     * Get service instance using locator
     */
    fun getServiceInstance(serviceClass: String): Any? {
        return serviceLocators[serviceClass]?.invoke()
    }

    internal fun generateContextId(): String = 
        "ctx-${System.currentTimeMillis()}-${kotlin.random.Random.nextInt()}"
}

/**
 * Type-safe Service Proxy (GWT pattern)
 */
class ServiceProxy<T : Any>(
    internal val serviceClass: KClass<T>,
    internal val requestFactory: EvolvedRequestFactory,
    internal val entityRegistry: EntityRegistry
) {
    
    internal val methodValidators = mutableMapOf<String, (Array<Any?>) -> Boolean>()
    internal val methodTokens = mutableMapOf<String, String>()

    /**
     * Invoke a method on the service
     */
    suspend fun invoke(
        methodName: String,
        vararg args: Any?,
        receiver: Receiver<Any?>? = null
    ): ServiceInvocation<T> {
        val methodToken = methodTokens.getOrPut(methodName) { 
            generateMethodToken(serviceClass, methodName) 
        }
        
        val invocation = ServiceInvocation<T>(
            serviceClass = serviceClass,
            methodName = methodName,
            methodToken = methodToken,
            args = args.toList().toIndexed(),
            receiver = receiver
        )
        
        // Validate method if validator exists
        methodValidators[methodName]?.let { validator ->
            if (!validator(args)) {
                throw IllegalArgumentException("Method validation failed: $methodName")
            }
        }
        
        return invocation
    }

    /**
     * Create a new entity
     */
    suspend fun <E : Any> create(
        entityClass: KClass<E>,
        initialData: Map<String, Any?> = emptyMap(),
        receiver: Receiver<E>? = null
    ): EntityCreation<E> {
        val entityToken = generateEntityToken(entityClass)
        
        return EntityCreation<E>(
            serviceClass = serviceClass,
            entityClass = entityClass,
            entityToken = entityToken,
            initialData = initialData,
            receiver = receiver
        )
    }

    /**
     * Register method validator
     */
    fun registerMethodValidator(methodName: String, validator: (Array<Any?>) -> Boolean) {
        methodValidators[methodName] = validator
    }

    internal fun generateMethodToken(serviceClass: KClass<*>, methodName: String): String =
        "${serviceClass.simpleName}.$methodName"

    internal fun generateEntityToken(entityClass: KClass<*>): String =
        entityClass.simpleName ?: "UnknownEntity"
}

/**
 * Entity Proxy for versioned entities (GWT pattern)
 */
class EntityProxy<T : Any>(
    internal val entityId: String,
    internal val entityClass: KClass<T>,
    internal val requestFactory: EvolvedRequestFactory,
    internal val entityRegistry: EntityRegistry
) {
    
    internal var version: EntityVersion? = null
    internal var data: T? = null
    internal val changes = mutableMapOf<String, Any?>()
    internal var isModified = false

    /**
     * Get current entity data
     */
    fun getData(): T? = data

    /**
     * Get current version
     */
    fun getVersion(): EntityVersion? = version

    /**
     * Check if entity has been modified
     */
    fun isModified(): Boolean = isModified

    /**
     * Set a property value (marks entity as modified)
     */
    fun setProperty(propertyName: String, value: Any?) {
        changes[propertyName] = value
        isModified = true
    }

    /**
     * Get a property value
     */
    fun getProperty(propertyName: String): Any? {
        return changes[propertyName] ?: data?.let { entity ->
            // Use reflection to get property value (simplified)
            entity.toString()
        }
    }

    /**
     * Save changes to server
     */
    suspend fun save(receiver: Receiver<T>? = null): EntityUpdate<T> {
        if (!isModified) {
            return EntityUpdate<T>(entityId, version, null, false)
        }
        
        val currentVersion = version ?: EntityVersion(entityId, 0L)
        val delta = EntityDelta(entityId, changes.toMap())
        
        return EntityUpdate<T>(
            entityId = entityId,
            version = currentVersion,
            delta = delta,
            isModified = true
        )
    }

    /**
     * Delete entity
     */
    suspend fun delete(receiver: Receiver<Unit>? = null): EntityDelete<T> {
        val currentVersion = version ?: EntityVersion(entityId, 0L)
        
        return EntityDelete<T>(
            entityId = entityId,
            version = currentVersion,
            receiver = receiver
        )
    }

    /**
     * Refresh entity from server
     */
    suspend fun refresh(receiver: Receiver<T>? = null): EntityRefresh<T> {
        return EntityRefresh<T>(
            entityId = entityId,
            entityClass = entityClass,
            receiver = receiver
        )
    }

    /**
     * Apply changes from server
     */
    fun applyChanges(newData: T, newVersion: EntityVersion) {
        data = newData
        version = newVersion
        changes.clear()
        isModified = false
    }
}

/**
 * Request Context for batching operations (GWT pattern)
 */
class RequestContext(
    internal val contextId: String,
    internal val requestFactory: EvolvedRequestFactory,
    internal val entityRegistry: EntityRegistry
) {
    
    internal val operations = mutableListOf<BatchedOperation>()
    internal val entityChanges = mutableMapOf<String, EntityChange>()

    /**
     * Add an invocation to the batch
     */
    fun <T : Any> addInvocation(invocation: ServiceInvocation<T>) {
        operations.add(BatchedOperation.Invocation(invocation))
    }

    /**
     * Add an entity creation to the batch
     */
    fun <T : Any> addCreation(creation: EntityCreation<T>) {
        operations.add(BatchedOperation.Creation(creation))
    }

    /**
     * Add an entity update to the batch
     */
    fun <T : Any> addUpdate(update: EntityUpdate<T>) {
        operations.add(BatchedOperation.Update(update))
    }

    /**
     * Add an entity deletion to the batch
     */
    fun <T : Any> addDeletion(deletion: EntityDelete<T>) {
        operations.add(BatchedOperation.Deletion(deletion))
    }

    /**
     * Fire the batch request
     */
    suspend fun fire(): BatchResult {
        val batchRequest = EvolvedRequest.Batch(
            operations = operations.map { it.toOperation() }
        )
        
        val response = requestFactory.processBatch(batchRequest)
        
        return BatchResult(
            contextId = contextId,
            response = response,
            operations = operations
        )
    }

    /**
     * Clear the batch
     */
    fun clear() {
        operations.clear()
        entityChanges.clear()
    }
}

/**
 * Entity Registry for managing entity lifecycle
 */
class EntityRegistry {
    
    internal val entities = mutableMapOf<String, Any>()
    internal val versions = mutableMapOf<String, EntityVersion>()
    internal val proxies = mutableMapOf<String, EntityProxy<*>>()

    /**
     * Register an entity
     */
    fun <T : Any> registerEntity(entityId: String, entity: T, version: EntityVersion) {
        entities[entityId] = entity
        versions[entityId] = version
    }

    /**
     * Get entity by ID
     */
    fun <T : Any> getEntity(entityId: String): T? {
        return entities[entityId] as? T
    }

    /**
     * Get entity version
     */
    fun getEntityVersion(entityId: String): EntityVersion? {
        return versions[entityId]
    }

    /**
     * Update entity
     */
    fun <T : Any> updateEntity(entityId: String, entity: T, version: EntityVersion) {
        entities[entityId] = entity
        versions[entityId] = version
    }

    /**
     * Delete entity
     */
    fun deleteEntity(entityId: String) {
        entities.remove(entityId)
        versions.remove(entityId)
        proxies.remove(entityId)
    }
}

// Core types

data class ServiceInvocation<T : Any>(
    val serviceClass: KClass<T>,
    val methodName: String,
    val methodToken: String,
    val args: Indexed<Any?>,
    val receiver: Receiver<Any?>?
)

data class EntityCreation<T : Any>(
    val serviceClass: KClass<*>,
    val entityClass: KClass<T>,
    val entityToken: String,
    val initialData: Map<String, Any?>,
    val receiver: Receiver<T>?
)

data class EntityUpdate<T : Any>(
    val entityId: String,
    val version: EntityVersion?,
    val delta: EntityDelta?,
    val isModified: Boolean
)

data class EntityDelete<T : Any>(
    val entityId: String,
    val version: EntityVersion,
    val receiver: Receiver<Unit>?
)

data class EntityRefresh<T : Any>(
    val entityId: String,
    val entityClass: KClass<T>,
    val receiver: Receiver<T>?
)

sealed class BatchedOperation {
    data class Invocation<T : Any>(val invocation: ServiceInvocation<T>) : BatchedOperation()
    data class Creation<T : Any>(val creation: EntityCreation<T>) : BatchedOperation()
    data class Update<T : Any>(val update: EntityUpdate<T>) : BatchedOperation()
    data class Deletion<T : Any>(val deletion: EntityDelete<T>) : BatchedOperation()
    
    fun toOperation(): Operation {
        return when (this) {
            is Invocation -> Operation.Invoke(
                serviceToken = invocation.serviceClass.simpleName ?: "",
                methodToken = invocation.methodToken,
                args = invocation.args
            )
            is Creation -> Operation.Create(
                serviceToken = creation.serviceClass.simpleName ?: "",
                entityToken = creation.entityToken,
                initialState = creation.initialData
            )
            is Update -> Operation.Update(
                serviceToken = "",
                entityToken = "",
                delta = update.delta ?: EntityDelta("", emptyMap()),
                version = update.version ?: EntityVersion("", 0L)
            )
            is Deletion -> Operation.Delete(
                serviceToken = "",
                entityToken = "",
                version = deletion.version
            )
        }
    }
}

data class BatchResult(
    val contextId: String,
    val response: EvolvedResponse,
    val operations: List<BatchedOperation>
)

data class EntityChange(
    val entityId: String,
    val propertyName: String,
    val oldValue: Any?,
    val newValue: Any?
)

interface Receiver<T> {
    fun onSuccess(response: T)
    fun onFailure(error: String)
} 