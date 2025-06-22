package borg.trikeshed.ccek

import borg.trikeshed.io.IODaemonConfig
import borg.trikeshed.io.IODaemonOperation
import borg.trikeshed.io.IODaemonResult
import borg.trikeshed.io.IODaemonStats
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/**
 * CCEK Service Interfaces
 * Defines the core services as CoroutineContext.Elements for the CCEK architecture.
 */

/**
 * High-performance asynchronous I/O service.
 */
interface IODaemon : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<IODaemon>

    suspend fun initialize(config: IODaemonConfig = IODaemonConfig())
    suspend fun shutdown()
    suspend fun submit(operation: IODaemonOperation): IODaemonResult
    suspend fun submitBatch(operations: List<IODaemonOperation>): List<IODaemonResult>
    fun completedOperations(): Flow<IODaemonResult>
    suspend fun getStats(): IODaemonStats
}

/**
 * Service for handling HTTP requests.
 */
interface HttpService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<HttpService>
    
    suspend fun start()
    suspend fun stop()
}

/**
 * Compressed Communication Engine Kit service
 */
interface CCekEngineService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key
    companion object Key : CoroutineContext.Key<CCekEngineService>
    
    fun initialize(compressionLevel: Int = 6, bufferSize: Int = 8192)
    fun cleanup()
    suspend fun send(target: String, data: ByteArray): Int
    suspend fun <T> sendObject(target: String, obj: T): Int where T : kotlinx.serialization.Serializable
    suspend fun receive(source: String): ByteArray
    suspend fun <T> receiveObject(source: String, type: kotlin.reflect.KClass<T>): T where T : kotlinx.serialization.Serializable
}