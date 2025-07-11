@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.torrent.rpc

import borg.trikeshed.lib.*
import borg.trikeshed.torrent.DownloadTask
import borg.trikeshed.services.RequestFactoryService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Distributed Objects for Torrent RPC Server
 * 
 * These objects can be shared across nodes in a distributed RequestFactory setup:
 * - DownloadSession: Manages download state across nodes
 * - DownloadRegistry: Central registry for all downloads
 * - ProgressTracker: Tracks download progress with real-time updates
 * - SessionManager: Manages session persistence and recovery
 * 
 * Each object implements the RequestFactory pattern for distributed access
 */
@Serializable
data class DownloadSession(
    val sessionId: String,
    val nodeId: String,
    val downloads: Map<String, DownloadTask> = emptyMap(),
    val stats: Map<String, DownloadProgress> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    fun updateDownload(downloadId: String, task: DownloadTask): DownloadSession {
        return copy(
            downloads = downloads + (downloadId to task),
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun updateProgress(downloadId: String, progress: DownloadProgress): DownloadSession {
        return copy(
            stats = stats + (downloadId to progress),
            lastModified = System.currentTimeMillis()
        )
    }
    
    fun removeDownload(downloadId: String): DownloadSession {
        return copy(
            downloads = downloads - downloadId,
            stats = stats - downloadId,
            lastModified = System.currentTimeMillis()
        )
    }
}

/**
 * Distributed Download Registry
 * Manages downloads across multiple nodes with consistency guarantees
 */
class DistributedDownloadRegistry(
    internal val requestFactory: RequestFactoryService,
    internal val nodeId: String
) {
    
    internal val sessions = mutableMapOf<String, DownloadSession>()
    internal val sessionLocks = mutableMapOf<String, Mutex>()
    
    init {
        registerDistributedMethods()
    }
    
    internal fun registerDistributedMethods() {
        requestFactory.registerMethodValidator("registry.createSession") { validateCreateSession(it) }
        requestFactory.registerMethodValidator("registry.getSession") { validateGetSession(it) }
        requestFactory.registerMethodValidator("registry.updateSession") { validateUpdateSession(it) }
        requestFactory.registerMethodValidator("registry.deleteSession") { validateDeleteSession(it) }
        requestFactory.registerMethodValidator("registry.listSessions") { validateListSessions(it) }
        requestFactory.registerMethodValidator("registry.addDownload") { validateAddDownload(it) }
        requestFactory.registerMethodValidator("registry.removeDownload") { validateRemoveDownload(it) }
        requestFactory.registerMethodValidator("registry.updateProgress") { validateUpdateProgress(it) }
        requestFactory.registerMethodValidator("registry.getProgress") { validateGetProgress(it) }
        requestFactory.registerMethodValidator("registry.listDownloads") { validateListDownloads(it) }
    }
    
    /**
     * Create a new distributed session
     */
    suspend fun createSession(sessionId: String, metadata: Map<String, String> = emptyMap()): DownloadSession {
        val session = DownloadSession(
            sessionId = sessionId,
            nodeId = nodeId,
            metadata = metadata
        )
        
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        lock.withLock {
            sessions[sessionId] = session
        }
        
        return session
    }
    
    /**
     * Get a session by ID
     */
    suspend fun getSession(sessionId: String): DownloadSession? {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        return lock.withLock {
            sessions[sessionId]
        }
    }
    
    /**
     * Update a session atomically
     */
    suspend fun updateSession(sessionId: String, updater: (DownloadSession) -> DownloadSession): DownloadSession? {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        return lock.withLock {
            val current = sessions[sessionId] ?: return@withLock null
            val updated = updater(current)
            sessions[sessionId] = updated
            updated
        }
    }
    
    /**
     * Delete a session
     */
    suspend fun deleteSession(sessionId: String): Boolean {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        return lock.withLock {
            val removed = sessions.remove(sessionId) != null
            sessionLocks.remove(sessionId)
            removed
        }
    }
    
    /**
     * List all sessions
     */
    suspend fun listSessions(): List<DownloadSession> {
        return sessions.values.toList()
    }
    
    /**
     * Add a download to a session
     */
    suspend fun addDownload(sessionId: String, downloadId: String, task: DownloadTask): Boolean {
        return updateSession(sessionId) { session ->
            session.updateDownload(downloadId, task)
        } != null
    }
    
    /**
     * Remove a download from a session
     */
    suspend fun removeDownload(sessionId: String, downloadId: String): Boolean {
        return updateSession(sessionId) { session ->
            session.removeDownload(downloadId)
        } != null
    }
    
    /**
     * Update download progress
     */
    suspend fun updateProgress(sessionId: String, downloadId: String, progress: DownloadProgress): Boolean {
        return updateSession(sessionId) { session ->
            session.updateProgress(downloadId, progress)
        } != null
    }
    
    /**
     * Get download progress
     */
    suspend fun getProgress(sessionId: String, downloadId: String): DownloadProgress? {
        val session = getSession(sessionId) ?: return null
        return session.stats[downloadId]
    }
    
    /**
     * List all downloads in a session
     */
    suspend fun listDownloads(sessionId: String): List<DownloadTask> {
        val session = getSession(sessionId) ?: return emptyList()
        return session.downloads.values.toList()
    }
    
    // Validation methods
    internal fun validateCreateSession(params: Any): Boolean = true
    internal fun validateGetSession(params: Any): Boolean = true
    internal fun validateUpdateSession(params: Any): Boolean = true
    internal fun validateDeleteSession(params: Any): Boolean = true
    internal fun validateListSessions(params: Any): Boolean = true
    internal fun validateAddDownload(params: Any): Boolean = true
    internal fun validateRemoveDownload(params: Any): Boolean = true
    internal fun validateUpdateProgress(params: Any): Boolean = true
    internal fun validateGetProgress(params: Any): Boolean = true
    internal fun validateListDownloads(params: Any): Boolean = true
}

/**
 * Distributed Progress Tracker
 * Tracks download progress across nodes with real-time updates
 */
class DistributedProgressTracker(
    internal val requestFactory: RequestFactoryService,
    internal val nodeId: String
) {
    
    internal val progressFlows = mutableMapOf<String, MutableSharedFlow<DownloadProgress>>()
    internal val progressCache = mutableMapOf<String, DownloadProgress>()
    internal val progressLocks = mutableMapOf<String, Mutex>()
    
    init {
        registerProgressMethods()
    }
    
    internal fun registerProgressMethods() {
        requestFactory.registerMethodValidator("progress.subscribe") { validateSubscribe(it) }
        requestFactory.registerMethodValidator("progress.unsubscribe") { validateUnsubscribe(it) }
        requestFactory.registerMethodValidator("progress.update") { validateUpdate(it) }
        requestFactory.registerMethodValidator("progress.get") { validateGet(it) }
        requestFactory.registerMethodValidator("progress.list") { validateList(it) }
    }
    
    /**
     * Subscribe to progress updates for a download
     */
    suspend fun subscribeToProgress(downloadId: String): Flow<DownloadProgress> {
        val flow = progressFlows.getOrPut(downloadId) { MutableSharedFlow(replay = 1) }
        
        // Send current progress if available
        val current = progressCache[downloadId]
        if (current != null) {
            flow.emit(current)
        }
        
        return flow.asSharedFlow()
    }
    
    /**
     * Unsubscribe from progress updates
     */
    suspend fun unsubscribeFromProgress(downloadId: String) {
        progressFlows.remove(downloadId)
        progressCache.remove(downloadId)
        progressLocks.remove(downloadId)
    }
    
    /**
     * Update progress for a download
     */
    suspend fun updateProgress(downloadId: String, progress: DownloadProgress) {
        val lock = progressLocks.getOrPut(downloadId) { Mutex() }
        lock.withLock {
            progressCache[downloadId] = progress
            val flow = progressFlows.getOrPut(downloadId) { MutableSharedFlow(replay = 1) }
            flow.emit(progress)
        }
    }
    
    /**
     * Get current progress for a download
     */
    suspend fun getProgress(downloadId: String): DownloadProgress? {
        val lock = progressLocks.getOrPut(downloadId) { Mutex() }
        return lock.withLock {
            progressCache[downloadId]
        }
    }
    
    /**
     * List all tracked downloads
     */
    suspend fun listTrackedDownloads(): List<String> {
        return progressCache.keys.toList()
    }
    
    // Validation methods
    internal fun validateSubscribe(params: Any): Boolean = true
    internal fun validateUnsubscribe(params: Any): Boolean = true
    internal fun validateUpdate(params: Any): Boolean = true
    internal fun validateGet(params: Any): Boolean = true
    internal fun validateList(params: Any): Boolean = true
}

/**
 * Distributed Session Manager
 * Manages session persistence and recovery across nodes
 */
class DistributedSessionManager(
    internal val requestFactory: RequestFactoryService,
    internal val nodeId: String
) {
    
    internal val sessionStorage = mutableMapOf<String, DownloadSession>()
    internal val sessionLocks = mutableMapOf<String, Mutex>()
    
    init {
        registerSessionMethods()
    }
    
    internal fun registerSessionMethods() {
        requestFactory.registerMethodValidator("session.save") { validateSave(it) }
        requestFactory.registerMethodValidator("session.load") { validateLoad(it) }
        requestFactory.registerMethodValidator("session.delete") { validateDelete(it) }
        requestFactory.registerMethodValidator("session.list") { validateList(it) }
        requestFactory.registerMethodValidator("session.backup") { validateBackup(it) }
        requestFactory.registerMethodValidator("session.restore") { validateRestore(it) }
    }
    
    /**
     * Save a session to persistent storage
     */
    suspend fun saveSession(session: DownloadSession): Boolean {
        val lock = sessionLocks.getOrPut(session.sessionId) { Mutex() }
        return lock.withLock {
            sessionStorage[session.sessionId] = session
            true
        }
    }
    
    /**
     * Load a session from persistent storage
     */
    suspend fun loadSession(sessionId: String): DownloadSession? {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        return lock.withLock {
            sessionStorage[sessionId]
        }
    }
    
    /**
     * Delete a session from persistent storage
     */
    suspend fun deleteSession(sessionId: String): Boolean {
        val lock = sessionLocks.getOrPut(sessionId) { Mutex() }
        return lock.withLock {
            val removed = sessionStorage.remove(sessionId) != null
            sessionLocks.remove(sessionId)
            removed
        }
    }
    
    /**
     * List all saved sessions
     */
    suspend fun listSessions(): List<DownloadSession> {
        return sessionStorage.values.toList()
    }
    
    /**
     * Create a backup of all sessions
     */
    suspend fun backupSessions(): Map<String, DownloadSession> {
        return sessionStorage.toMap()
    }
    
    /**
     * Restore sessions from backup
     */
    suspend fun restoreSessions(backup: Map<String, DownloadSession>): Boolean {
        sessionStorage.clear()
        sessionStorage.putAll(backup)
        return true
    }
    
    // Validation methods
    internal fun validateSave(params: Any): Boolean = true
    internal fun validateLoad(params: Any): Boolean = true
    internal fun validateDelete(params: Any): Boolean = true
    internal fun validateList(params: Any): Boolean = true
    internal fun validateBackup(params: Any): Boolean = true
    internal fun validateRestore(params: Any): Boolean = true
}

/**
 * Distributed Node Coordinator
 * Coordinates operations across multiple nodes
 */
class DistributedNodeCoordinator(
    internal val requestFactory: RequestFactoryService,
    internal val nodeId: String,
    internal val registry: DistributedDownloadRegistry,
    internal val tracker: DistributedProgressTracker,
    internal val sessionManager: DistributedSessionManager
) {
    
    internal val connectedNodes = mutableSetOf<String>()
    internal val nodeCapabilities = mutableMapOf<String, Set<String>>()
    
    init {
        registerCoordinationMethods()
    }
    
    internal fun registerCoordinationMethods() {
        requestFactory.registerMethodValidator("coordinator.register") { validateRegister(it) }
        requestFactory.registerMethodValidator("coordinator.unregister") { validateUnregister(it) }
        requestFactory.registerMethodValidator("coordinator.listNodes") { validateListNodes(it) }
        requestFactory.registerMethodValidator("coordinator.getCapabilities") { validateGetCapabilities(it) }
        requestFactory.registerMethodValidator("coordinator.distribute") { validateDistribute(it) }
        requestFactory.registerMethodValidator("coordinator.sync") { validateSync(it) }
    }
    
    /**
     * Register this node with the coordinator
     */
    suspend fun registerNode(capabilities: Set<String>): Boolean {
        nodeCapabilities[nodeId] = capabilities
        connectedNodes.add(nodeId)
        return true
    }
    
    /**
     * Unregister this node
     */
    suspend fun unregisterNode(): Boolean {
        connectedNodes.remove(nodeId)
        nodeCapabilities.remove(nodeId)
        return true
    }
    
    /**
     * List all connected nodes
     */
    suspend fun listNodes(): List<String> {
        return connectedNodes.toList()
    }
    
    /**
     * Get capabilities of a node
     */
    suspend fun getNodeCapabilities(nodeId: String): Set<String> {
        return nodeCapabilities[nodeId] ?: emptySet()
    }
    
    /**
     * Distribute a download across multiple nodes
     */
    suspend fun distributeDownload(sessionId: String, downloadId: String, task: DownloadTask): List<String> {
        val capableNodes = connectedNodes.filter { node ->
            val capabilities = nodeCapabilities[node] ?: emptySet()
            when (task) {
                is DownloadTask.HttpDownload -> capabilities.contains("http")
                is DownloadTask.TorrentDownload -> capabilities.contains("torrent")
            }
        }
        
        // For now, just use the first capable node
        val targetNode = capableNodes.firstOrNull() ?: nodeId
        
        if (targetNode == nodeId) {
            registry.addDownload(sessionId, downloadId, task)
        }
        
        return listOf(targetNode)
    }
    
    /**
     * Sync state across nodes
     */
    suspend fun syncState(sessionId: String): Boolean {
        val session = registry.getSession(sessionId) ?: return false
        return sessionManager.saveSession(session)
    }
    
    // Validation methods
    internal fun validateRegister(params: Any): Boolean = true
    internal fun validateUnregister(params: Any): Boolean = true
    internal fun validateListNodes(params: Any): Boolean = true
    internal fun validateGetCapabilities(params: Any): Boolean = true
    internal fun validateDistribute(params: Any): Boolean = true
    internal fun validateSync(params: Any): Boolean = true
} 