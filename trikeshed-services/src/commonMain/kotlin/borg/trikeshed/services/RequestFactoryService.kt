@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.Flow

/**
 * KMP RequestFactory Service Interface - No google/gwt JVM dependencies
 * Integrated with reactor HTTP server toolkit for JSON arsenal and wireproto
 */
interface RequestFactoryService {
    /**
     * Process a RequestFactory call and return response payload
     */
    fun process(requestPayload: Indexed<Byte>): Indexed<Byte>
    
    /**
     * Register a service locator for dependency injection
     */
    fun registerServiceLocator(serviceClass: String, locator: () -> Any)
    
    /**
     * Register a method validator for security
     */
    fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean)

    /**
     * Invoke a service method asynchronously
     */
    suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte>
}

/**
 * DealService coroutine context element for reactor integration
 */
interface DealService : kotlin.coroutines.CoroutineContext.Element {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<DealService>
    
    override val key: kotlin.coroutines.CoroutineContext.Key<*>
        get() = Key
    
    /**
     * Process a deal request
     */
    suspend fun process(data: Indexed<Byte>): Indexed<Byte>
    
    /**
     * Get deal information
     */
    fun getDealInfo(dealId: String): String
    
    /**
     * Create a new deal
     */
    fun createDeal(dealData: Indexed<Byte>): String
}

/**
 * Empty service marker for lightweight service containers
 */
object EmptyRequestFactoryService : RequestFactoryService {
    override fun process(requestPayload: Indexed<Byte>): Indexed<Byte> = emptyIndexed()
    override fun registerServiceLocator(serviceClass: String, locator: () -> Any) {}
    override fun registerMethodValidator(methodName: String, validator: (Any) -> Boolean) {}
    override suspend fun invokeService(serviceName: String, data: Indexed<Byte>): Indexed<Byte> = emptyIndexed()
}