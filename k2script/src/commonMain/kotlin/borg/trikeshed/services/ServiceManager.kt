@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlin.reflect.KClass

/**
 * Service Manager for dependency injection and lifecycle management
 * Real implementation for k2script services
 */
object ServiceManager {
    val services = mutableMapOf<KClass<*>, Any>()
    val serviceJobs = mutableMapOf<KClass<*>, Job>()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    inline fun <reified T : Any> register(service: T): ServiceManager {
        services[T::class] = service
        return this
    }
    
    inline fun <reified T : Any> get(): T {
        return services[T::class] as? T
            ?: throw IllegalStateException("Service not registered: ${T::class.simpleName}")
    }
    
    inline fun <reified T : Any> getOrNull(): T? {
        return services[T::class] as? T
    }
    
    inline fun <reified T : Service> start(): ServiceManager {
        val service = get<T>()
        if (!serviceJobs.containsKey(T::class)) {
            serviceJobs[T::class] = scope.launch {
                service.start()
            }
        }
        return this
    }
    
    inline fun <reified T : Service> stop(): ServiceManager {
        serviceJobs[T::class]?.cancel()
        serviceJobs.remove(T::class)
        val service = getOrNull<T>()
        runBlocking {
            service?.stop()
        }
        return this
    }
    
    fun stopAll() {
        serviceJobs.values.forEach { it.cancel() }
        serviceJobs.clear()
        services.values.filterIsInstance<Service>().forEach {
            runBlocking { it.stop() }
        }
    }
    
    fun clear() {
        stopAll()
        services.clear()
    }
}

/**
 * Base interface for managed services
 */
interface Service {
    suspend fun start()
    suspend fun stop()
}

/**
 * Abstract base class for services with lifecycle
 */
abstract class AbstractService : Service {
    var isRunning = false
    
    override suspend fun start() {
        if (!isRunning) {
            isRunning = true
            onStart()
        }
    }
    
    override suspend fun stop() {
        if (isRunning) {
            isRunning = false
            onStop()
        }
    }
    
    abstract suspend fun onStart()
    abstract suspend fun onStop()
}