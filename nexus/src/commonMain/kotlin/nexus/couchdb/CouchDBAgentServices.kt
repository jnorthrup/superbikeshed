package nexus.couchdb

import borg.trikeshed.lib.*
import borg.trikeshed.couchdb.*
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * CouchDB Agent Services
 * 
 * Provides specialized agent services for CouchDB operations:
 * - View Processing Agent
 * - API Handling Agent
 * - Document Operations Agent
 * - Change Feed Agent
 * - Replication Agent
 * - Security Agent
 * - Performance Monitoring Agent
 * - Design Document Agent
 */
object CouchDBAgentServices {

    /**
     * View Processing Agent
     * Handles CouchDB view processing and optimization
     */
    class ViewProcessingAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val viewCache = mutableMapOf<String, CachedViewResult>()
        private val processingQueue = Channel<ViewProcessingTask>(Channel.UNLIMITED)
        private var isActive = false

        @Serializable
        data class CachedViewResult(
            val result: ViewResponse<JsonElement, JsonElement>,
            val timestamp: Instant,
            val ttl: Duration = 5.minutes
        )

        @Serializable
        data class ViewProcessingTask(
            val taskId: String,
            val databaseName: String,
            val designDoc: String,
            val viewName: String,
            val queryParams: ViewQueryParams,
            val priority: TaskPriority = TaskPriority.NORMAL,
            val useCache: Boolean = true,
            val requesterId: NUID
        )

        enum class TaskPriority {
            LOW, NORMAL, HIGH, URGENT
        }

        suspend fun start() {
            isActive = true
            launch { processViewQueue() }
        }

        suspend fun stop() {
            isActive = false
            processingQueue.close()
        }

        suspend fun processView(task: ViewProcessingTask): ViewProcessingResult {
            val cacheKey = generateCacheKey(task)
            
            // Check cache first
            if (task.useCache) {
                val cached = viewCache[cacheKey]
                if (cached != null && Clock.System.now() - cached.timestamp < cached.ttl) {
                    return ViewProcessingResult.Success(cached.result)
                }
            }

            return try {
                val startTime = Clock.System.now()
                
                val result = couchClient.queryView(
                    dbName = task.databaseName,
                    designDoc = task.designDoc,
                    viewName = task.viewName,
                    params = task.queryParams
                )
                
                val processingTime = Clock.System.now() - startTime
                
                // Cache the result
                if (task.useCache) {
                    viewCache[cacheKey] = CachedViewResult(
                        result = result,
                        timestamp = Clock.System.now()
                    )
                }
                
                ViewProcessingResult.Success(result)
            } catch (e: Exception) {
                ViewProcessingResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun processViewQueue() {
            while (isActive) {
                try {
                    val task = processingQueue.receive()
                    val result = processView(task)
                    
                    // Send result back to requester
                    // Implementation would depend on communication mechanism
                    
                } catch (e: Exception) {
                    println("View processing error: ${e.message}")
                }
            }
        }

        private fun generateCacheKey(task: ViewProcessingTask): String {
            return "${task.databaseName}:${task.designDoc}:${task.viewName}:${task.queryParams.hashCode()}"
        }

        sealed class ViewProcessingResult {
            data class Success(val result: ViewResponse<JsonElement, JsonElement>) : ViewProcessingResult()
            data class Error(val message: String) : ViewProcessingResult()
        }
    }

    /**
     * API Handling Agent
     * Manages HTTP API requests and responses
     */
    class APIHandlingAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val requestHandlers = mutableMapOf<String, RequestHandler>()
        private val responseCache = mutableMapOf<String, CachedResponse>()
        private val rateLimiters = mutableMapOf<String, RateLimiter>()

        @Serializable
        data class CachedResponse(
            val response: String,
            val statusCode: Int,
            val headers: Map<String, String>,
            val timestamp: Instant,
            val ttl: Duration = 1.minutes
        )

        interface RequestHandler {
            suspend fun handle(request: APIRequest): APIResponse
        }

        @Serializable
        data class APIRequest(
            val method: String,
            val path: String,
            val headers: Map<String, String>,
            val body: String?,
            val queryParams: Map<String, String>,
            val requesterId: NUID,
            val timestamp: Instant = Clock.System.now()
        )

        @Serializable
        data class APIResponse(
            val statusCode: Int,
            val headers: Map<String, String>,
            val body: String,
            val processingTime: Duration
        )

        suspend fun registerHandler(path: String, handler: RequestHandler) {
            requestHandlers[path] = handler
        }

        suspend fun handleRequest(request: APIRequest): APIResponse {
            // Check rate limiting
            val rateLimiter = getRateLimiter(request.requesterId.toString())
            if (!rateLimiter.allowRequest()) {
                return APIResponse(
                    statusCode = 429,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"error": "Rate limit exceeded"}""",
                    processingTime = Duration.ZERO
                )
            }

            // Check cache
            val cacheKey = generateCacheKey(request)
            val cached = responseCache[cacheKey]
            if (cached != null && Clock.System.now() - cached.timestamp < cached.ttl) {
                return APIResponse(
                    statusCode = cached.statusCode,
                    headers = cached.headers,
                    body = cached.response,
                    processingTime = Duration.ZERO
                )
            }

            // Find appropriate handler
            val handler = findHandler(request.path)
            return if (handler != null) {
                val startTime = Clock.System.now()
                val response = handler.handle(request)
                val processingTime = Clock.System.now() - startTime

                // Cache successful responses
                if (response.statusCode < 400) {
                    responseCache[cacheKey] = CachedResponse(
                        response = response.body,
                        statusCode = response.statusCode,
                        headers = response.headers,
                        timestamp = Clock.System.now()
                    )
                }

                response.copy(processingTime = processingTime)
            } else {
                APIResponse(
                    statusCode = 404,
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"error": "Handler not found"}""",
                    processingTime = Duration.ZERO
                )
            }
        }

        private fun findHandler(path: String): RequestHandler? {
            return requestHandlers.entries.find { (pattern, _) ->
                path.startsWith(pattern)
            }?.value
        }

        private fun generateCacheKey(request: APIRequest): String {
            return "${request.method}:${request.path}:${request.body.hashCode()}"
        }

        private fun getRateLimiter(requesterId: String): RateLimiter {
            return rateLimiters.getOrPut(requesterId) { RateLimiter() }
        }

        class RateLimiter(
            private val maxRequests: Int = 100,
            private val windowSize: Duration = 1.minutes
        ) {
            private val requests = mutableListOf<Instant>()

            fun allowRequest(): Boolean {
                val now = Clock.System.now()
                val windowStart = now - windowSize
                
                // Remove old requests
                requests.removeAll { it < windowStart }
                
                // Check if under limit
                return if (requests.size < maxRequests) {
                    requests.add(now)
                    true
                } else {
                    false
                }
            }
        }
    }

    /**
     * Document Operations Agent
     * Handles CRUD operations on CouchDB documents
     */
    class DocumentOperationsAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val operationQueue = Channel<DocumentOperation>(Channel.UNLIMITED)
        private val conflictResolvers = mutableMapOf<String, ConflictResolver>()
        private val validators = mutableMapOf<String, DocumentValidator>()

        @Serializable
        data class DocumentOperation(
            val operationId: String,
            val type: OperationType,
            val databaseName: String,
            val documentId: String?,
            val document: CouchDocument?,
            val revision: String?,
            val requesterId: NUID,
            val timestamp: Instant = Clock.System.now()
        )

        enum class OperationType {
            CREATE, READ, UPDATE, DELETE, BULK_OPERATION
        }

        interface ConflictResolver {
            suspend fun resolveConflict(
                originalDoc: CouchDocument,
                conflictingDoc: CouchDocument
            ): CouchDocument
        }

        interface DocumentValidator {
            suspend fun validate(document: CouchDocument): ValidationResult
        }

        @Serializable
        data class ValidationResult(
            val isValid: Boolean,
            val errors: List<String> = emptyList()
        )

        suspend fun start() {
            launch { processOperationQueue() }
        }

        suspend fun performOperation(operation: DocumentOperation): OperationResult {
            return when (operation.type) {
                OperationType.CREATE -> createDocument(operation)
                OperationType.READ -> readDocument(operation)
                OperationType.UPDATE -> updateDocument(operation)
                OperationType.DELETE -> deleteDocument(operation)
                OperationType.BULK_OPERATION -> performBulkOperation(operation)
            }
        }

        private suspend fun createDocument(operation: DocumentOperation): OperationResult {
            return try {
                val document = operation.document ?: throw IllegalArgumentException("Document required for create operation")
                
                // Validate document
                val validator = validators[operation.databaseName]
                if (validator != null) {
                    val validation = validator.validate(document)
                    if (!validation.isValid) {
                        return OperationResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                    }
                }
                
                val response = couchClient.putDocument(operation.databaseName, document)
                
                if (response.ok) {
                    OperationResult.Success(response)
                } else {
                    OperationResult.Error("Failed to create document")
                }
            } catch (e: Exception) {
                OperationResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun readDocument(operation: DocumentOperation): OperationResult {
            return try {
                val documentId = operation.documentId ?: throw IllegalArgumentException("Document ID required for read operation")
                val document = couchClient.getDocument(operation.databaseName, documentId)
                
                if (document != null) {
                    OperationResult.Success(document)
                } else {
                    OperationResult.Error("Document not found")
                }
            } catch (e: Exception) {
                OperationResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun updateDocument(operation: DocumentOperation): OperationResult {
            return try {
                val document = operation.document ?: throw IllegalArgumentException("Document required for update operation")
                
                // Validate document
                val validator = validators[operation.databaseName]
                if (validator != null) {
                    val validation = validator.validate(document)
                    if (!validation.isValid) {
                        return OperationResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                    }
                }
                
                val response = couchClient.putDocument(operation.databaseName, document)
                
                if (response.ok) {
                    OperationResult.Success(response)
                } else {
                    OperationResult.Error("Failed to update document")
                }
            } catch (e: Exception) {
                OperationResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun deleteDocument(operation: DocumentOperation): OperationResult {
            return try {
                val documentId = operation.documentId ?: throw IllegalArgumentException("Document ID required for delete operation")
                val revision = operation.revision ?: throw IllegalArgumentException("Revision required for delete operation")
                
                val response = couchClient.deleteDocument(operation.databaseName, documentId, revision)
                
                if (response.ok) {
                    OperationResult.Success(response)
                } else {
                    OperationResult.Error("Failed to delete document")
                }
            } catch (e: Exception) {
                OperationResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun performBulkOperation(operation: DocumentOperation): OperationResult {
            return try {
                // Implementation would handle bulk operations
                OperationResult.Error("Bulk operations not implemented")
            } catch (e: Exception) {
                OperationResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun processOperationQueue() {
            while (true) {
                try {
                    val operation = operationQueue.receive()
                    val result = performOperation(operation)
                    
                    // Send result back to requester
                    // Implementation would depend on communication mechanism
                    
                } catch (e: Exception) {
                    println("Document operation error: ${e.message}")
                }
            }
        }

        sealed class OperationResult {
            data class Success(val data: Any) : OperationResult()
            data class Error(val message: String) : OperationResult()
        }
    }

    /**
     * Change Feed Agent
     * Manages CouchDB change feeds and subscriptions
     */
    class ChangeFeedAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val subscriptions = mutableMapOf<String, MutableList<ChangeFeedSubscription>>()
        private val changeProcessors = mutableMapOf<String, ChangeProcessor>()
        private val filterFunctions = mutableMapOf<String, FilterFunction>()

        @Serializable
        data class ChangeFeedSubscription(
            val subscriptionId: String,
            val databaseName: String,
            val filter: String?,
            val callback: (CouchChange) -> Unit,
            val requesterId: NUID,
            val active: Boolean = true
        )

        interface ChangeProcessor {
            suspend fun processChange(change: CouchChange)
        }

        interface FilterFunction {
            fun shouldInclude(change: CouchChange): Boolean
        }

        suspend fun subscribeToChanges(
            databaseName: String,
            filter: String?,
            callback: (CouchChange) -> Unit,
            requesterId: NUID
        ): String {
            val subscriptionId = generateSubscriptionId()
            
            val subscription = ChangeFeedSubscription(
                subscriptionId = subscriptionId,
                databaseName = databaseName,
                filter = filter,
                callback = callback,
                requesterId = requesterId
            )
            
            subscriptions.getOrPut(databaseName) { mutableListOf() }.add(subscription)
            
            // Start monitoring changes if not already started
            startChangeMonitoring(databaseName)
            
            return subscriptionId
        }

        suspend fun unsubscribeFromChanges(subscriptionId: String): Boolean {
            subscriptions.values.forEach { subscriptionList ->
                val subscription = subscriptionList.find { it.subscriptionId == subscriptionId }
                if (subscription != null) {
                    subscriptionList.remove(subscription)
                    return true
                }
            }
            return false
        }

        private suspend fun startChangeMonitoring(databaseName: String) {
            launch {
                val params = ChangesFeedParams(
                    since = "0",
                    feed = "continuous",
                    include_docs = true
                )
                
                couchClient.getChanges(databaseName, params).collect { change ->
                    processChange(databaseName, change)
                }
            }
        }

        private suspend fun processChange(databaseName: String, change: CouchChange) {
            val databaseSubscriptions = subscriptions[databaseName] ?: return
            
            databaseSubscriptions.forEach { subscription ->
                if (subscription.active) {
                    // Apply filter if specified
                    val shouldProcess = if (subscription.filter != null) {
                        val filter = filterFunctions[subscription.filter]
                        filter?.shouldInclude(change) ?: true
                    } else {
                        true
                    }
                    
                    if (shouldProcess) {
                        try {
                            subscription.callback(change)
                        } catch (e: Exception) {
                            println("Error in change feed callback: ${e.message}")
                        }
                    }
                }
            }
        }

        private fun generateSubscriptionId(): String {
            return "sub_${agentId}_${Clock.System.now().toEpochMilliseconds()}"
        }
    }

    /**
     * Replication Agent
     * Manages CouchDB replication between databases
     */
    class ReplicationAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val activeReplications = mutableMapOf<String, ReplicationJob>()
        private val replicationHistory = mutableListOf<ReplicationEvent>()

        @Serializable
        data class ReplicationJob(
            val jobId: String,
            val source: String,
            val target: String,
            val continuous: Boolean,
            val status: ReplicationStatus,
            val startTime: Instant,
            val lastSync: Instant? = null,
            val errorCount: Int = 0,
            val documentsReplicated: Long = 0
        )

        enum class ReplicationStatus {
            IDLE, RUNNING, COMPLETED, FAILED, PAUSED
        }

        @Serializable
        data class ReplicationEvent(
            val jobId: String,
            val eventType: ReplicationEventType,
            val details: String,
            val timestamp: Instant = Clock.System.now()
        )

        enum class ReplicationEventType {
            STARTED, COMPLETED, FAILED, PAUSED, RESUMED, DOCUMENT_SYNCED
        }

        suspend fun startReplication(
            source: String,
            target: String,
            continuous: Boolean = false
        ): String {
            val jobId = generateJobId()
            
            val job = ReplicationJob(
                jobId = jobId,
                source = source,
                target = target,
                continuous = continuous,
                status = ReplicationStatus.RUNNING,
                startTime = Clock.System.now()
            )
            
            activeReplications[jobId] = job
            
            // Start replication in background
            launch { performReplication(job) }
            
            // Record event
            replicationHistory.add(
                ReplicationEvent(
                    jobId = jobId,
                    eventType = ReplicationEventType.STARTED,
                    details = "Replication started from $source to $target"
                )
            )
            
            return jobId
        }

        suspend fun stopReplication(jobId: String): Boolean {
            val job = activeReplications[jobId] ?: return false
            
            job.copy(status = ReplicationStatus.PAUSED)
            activeReplications[jobId] = job
            
            // Record event
            replicationHistory.add(
                ReplicationEvent(
                    jobId = jobId,
                    eventType = ReplicationEventType.PAUSED,
                    details = "Replication paused"
                )
            )
            
            return true
        }

        private suspend fun performReplication(job: ReplicationJob) {
            while (job.status == ReplicationStatus.RUNNING) {
                try {
                    val request = ReplicationRequest(
                        source = job.source,
                        target = job.target,
                        continuous = job.continuous,
                        create_target = true
                    )
                    
                    val response = couchClient.replicate(request)
                    
                    if (response.ok) {
                        val updatedJob = job.copy(
                            status = if (job.continuous) ReplicationStatus.RUNNING else ReplicationStatus.COMPLETED,
                            lastSync = Clock.System.now(),
                            documentsReplicated = job.documentsReplicated + 1
                        )
                        
                        activeReplications[job.jobId] = updatedJob
                        
                        // Record event
                        replicationHistory.add(
                            ReplicationEvent(
                                jobId = job.jobId,
                                eventType = ReplicationEventType.DOCUMENT_SYNCED,
                                details = "Document synced successfully"
                            )
                        )
                        
                        if (!job.continuous) {
                            break
                        }
                    } else {
                        val updatedJob = job.copy(
                            status = ReplicationStatus.FAILED,
                            errorCount = job.errorCount + 1
                        )
                        
                        activeReplications[job.jobId] = updatedJob
                        
                        // Record event
                        replicationHistory.add(
                            ReplicationEvent(
                                jobId = job.jobId,
                                eventType = ReplicationEventType.FAILED,
                                details = "Replication failed: ${response.session_id}"
                            )
                        )
                    }
                    
                    delay(30.seconds) // Wait before next sync
                    
                } catch (e: Exception) {
                    val updatedJob = job.copy(
                        status = ReplicationStatus.FAILED,
                        errorCount = job.errorCount + 1
                    )
                    
                    activeReplications[job.jobId] = updatedJob
                    
                    // Record event
                    replicationHistory.add(
                        ReplicationEvent(
                            jobId = job.jobId,
                            eventType = ReplicationEventType.FAILED,
                            details = "Replication error: ${e.message}"
                        )
                    )
                    
                    delay(60.seconds) // Wait longer on error
                }
            }
        }

        private fun generateJobId(): String {
            return "rep_${agentId}_${Clock.System.now().toEpochMilliseconds()}"
        }

        fun getReplicationStatus(jobId: String): ReplicationJob? {
            return activeReplications[jobId]
        }

        fun getReplicationHistory(jobId: String): List<ReplicationEvent> {
            return replicationHistory.filter { it.jobId == jobId }
        }
    }

    /**
     * Security Agent
     * Handles authentication, authorization, and security validation
     */
    class SecurityAgent(
        private val agentId: NUID
    ) {
        private val authenticationProviders = mutableMapOf<String, AuthenticationProvider>()
        private val authorizationPolicies = mutableMapOf<String, AuthorizationPolicy>()
        private val securityAudit = mutableListOf<SecurityAuditEvent>()

        interface AuthenticationProvider {
            suspend fun authenticate(credentials: AuthenticationCredentials): AuthenticationResult
        }

        interface AuthorizationPolicy {
            suspend fun authorize(
                user: AuthenticatedUser,
                resource: String,
                action: String
            ): AuthorizationResult
        }

        @Serializable
        data class AuthenticationCredentials(
            val username: String,
            val password: String,
            val provider: String = "default"
        )

        @Serializable
        data class AuthenticationResult(
            val success: Boolean,
            val user: AuthenticatedUser? = null,
            val token: String? = null,
            val error: String? = null
        )

        @Serializable
        data class AuthenticatedUser(
            val userId: String,
            val username: String,
            val roles: Set<String>,
            val permissions: Set<String>,
            val authenticatedAt: Instant = Clock.System.now()
        )

        @Serializable
        data class AuthorizationResult(
            val allowed: Boolean,
            val reason: String? = null
        )

        @Serializable
        data class SecurityAuditEvent(
            val eventId: String,
            val eventType: SecurityEventType,
            val userId: String?,
            val resource: String?,
            val action: String?,
            val success: Boolean,
            val details: String,
            val timestamp: Instant = Clock.System.now()
        )

        enum class SecurityEventType {
            AUTHENTICATION_ATTEMPT,
            AUTHENTICATION_SUCCESS,
            AUTHENTICATION_FAILURE,
            AUTHORIZATION_CHECK,
            AUTHORIZATION_GRANTED,
            AUTHORIZATION_DENIED,
            RESOURCE_ACCESS,
            POLICY_VIOLATION
        }

        suspend fun registerAuthenticationProvider(
            name: String,
            provider: AuthenticationProvider
        ) {
            authenticationProviders[name] = provider
        }

        suspend fun registerAuthorizationPolicy(
            name: String,
            policy: AuthorizationPolicy
        ) {
            authorizationPolicies[name] = policy
        }

        suspend fun authenticate(credentials: AuthenticationCredentials): AuthenticationResult {
            val provider = authenticationProviders[credentials.provider]
                ?: return AuthenticationResult(
                    success = false,
                    error = "Authentication provider not found"
                )
            
            val result = provider.authenticate(credentials)
            
            // Record audit event
            securityAudit.add(
                SecurityAuditEvent(
                    eventId = generateEventId(),
                    eventType = if (result.success) SecurityEventType.AUTHENTICATION_SUCCESS else SecurityEventType.AUTHENTICATION_FAILURE,
                    userId = credentials.username,
                    success = result.success,
                    details = result.error ?: "Authentication successful"
                )
            )
            
            return result
        }

        suspend fun authorize(
            user: AuthenticatedUser,
            resource: String,
            action: String
        ): AuthorizationResult {
            val policy = authorizationPolicies["default"]
                ?: return AuthorizationResult(
                    allowed = false,
                    reason = "No authorization policy found"
                )
            
            val result = policy.authorize(user, resource, action)
            
            // Record audit event
            securityAudit.add(
                SecurityAuditEvent(
                    eventId = generateEventId(),
                    eventType = if (result.allowed) SecurityEventType.AUTHORIZATION_GRANTED else SecurityEventType.AUTHORIZATION_DENIED,
                    userId = user.userId,
                    resource = resource,
                    action = action,
                    success = result.allowed,
                    details = result.reason ?: "Authorization check completed"
                )
            )
            
            return result
        }

        private fun generateEventId(): String {
            return "sec_${agentId}_${Clock.System.now().toEpochMilliseconds()}"
        }

        fun getAuditEvents(
            userId: String? = null,
            eventType: SecurityEventType? = null,
            limit: Int = 100
        ): List<SecurityAuditEvent> {
            return securityAudit
                .filter { event ->
                    (userId == null || event.userId == userId) &&
                    (eventType == null || event.eventType == eventType)
                }
                .takeLast(limit)
        }
    }

    /**
     * Performance Monitoring Agent
     * Monitors and optimizes CouchDB performance
     */
    class PerformanceMonitoringAgent(
        private val agentId: NUID
    ) {
        private val performanceMetrics = mutableMapOf<String, PerformanceMetric>()
        private val optimizationRules = mutableListOf<OptimizationRule>()
        private val alerts = mutableListOf<PerformanceAlert>()

        @Serializable
        data class PerformanceMetric(
            val metricId: String,
            val name: String,
            val value: Double,
            val unit: String,
            val timestamp: Instant = Clock.System.now(),
            val tags: Map<String, String> = emptyMap()
        )

        @Serializable
        data class OptimizationRule(
            val ruleId: String,
            val name: String,
            val condition: (PerformanceMetric) -> Boolean,
            val action: (PerformanceMetric) -> Unit,
            val enabled: Boolean = true
        )

        @Serializable
        data class PerformanceAlert(
            val alertId: String,
            val severity: AlertSeverity,
            val message: String,
            val metric: PerformanceMetric?,
            val timestamp: Instant = Clock.System.now(),
            val acknowledged: Boolean = false
        )

        enum class AlertSeverity {
            INFO, WARNING, ERROR, CRITICAL
        }

        suspend fun recordMetric(metric: PerformanceMetric) {
            performanceMetrics[metric.metricId] = metric
            
            // Check optimization rules
            optimizationRules.forEach { rule ->
                if (rule.enabled && rule.condition(metric)) {
                    try {
                        rule.action(metric)
                    } catch (e: Exception) {
                        println("Optimization rule error: ${e.message}")
                    }
                }
            }
            
            // Check for alerts
            checkForAlerts(metric)
        }

        suspend fun addOptimizationRule(rule: OptimizationRule) {
            optimizationRules.add(rule)
        }

        private suspend fun checkForAlerts(metric: PerformanceMetric) {
            // Example alert conditions
            when (metric.name) {
                "response_time" -> {
                    if (metric.value > 1000.0) { // 1 second
                        createAlert(
                            severity = AlertSeverity.WARNING,
                            message = "High response time detected: ${metric.value}ms",
                            metric = metric
                        )
                    }
                }
                "error_rate" -> {
                    if (metric.value > 0.05) { // 5%
                        createAlert(
                            severity = AlertSeverity.ERROR,
                            message = "High error rate detected: ${metric.value * 100}%",
                            metric = metric
                        )
                    }
                }
                "memory_usage" -> {
                    if (metric.value > 0.9) { // 90%
                        createAlert(
                            severity = AlertSeverity.CRITICAL,
                            message = "High memory usage detected: ${metric.value * 100}%",
                            metric = metric
                        )
                    }
                }
            }
        }

        private suspend fun createAlert(
            severity: AlertSeverity,
            message: String,
            metric: PerformanceMetric?
        ) {
            val alert = PerformanceAlert(
                alertId = generateAlertId(),
                severity = severity,
                message = message,
                metric = metric
            )
            
            alerts.add(alert)
        }

        private fun generateAlertId(): String {
            return "perf_${agentId}_${Clock.System.now().toEpochMilliseconds()}"
        }

        fun getMetrics(
            name: String? = null,
            since: Instant? = null,
            limit: Int = 100
        ): List<PerformanceMetric> {
            return performanceMetrics.values
                .filter { metric ->
                    (name == null || metric.name == name) &&
                    (since == null || metric.timestamp >= since)
                }
                .sortedBy { it.timestamp }
                .takeLast(limit)
        }

        fun getAlerts(
            severity: AlertSeverity? = null,
            acknowledged: Boolean? = null,
            limit: Int = 100
        ): List<PerformanceAlert> {
            return alerts
                .filter { alert ->
                    (severity == null || alert.severity == severity) &&
                    (acknowledged == null || alert.acknowledged == acknowledged)
                }
                .sortedBy { it.timestamp }
                .takeLast(limit)
        }
    }

    /**
     * Design Document Agent
     * Manages CouchDB design documents and views
     */
    class DesignDocumentAgent(
        private val couchClient: CouchClient,
        private val agentId: NUID
    ) {
        private val designDocumentCache = mutableMapOf<String, CachedDesignDocument>()
        private val viewValidators = mutableListOf<ViewValidator>()
        private val deploymentHistory = mutableListOf<DeploymentEvent>()

        @Serializable
        data class CachedDesignDocument(
            val designDoc: DesignDocument,
            val timestamp: Instant,
            val version: String
        )

        @Serializable
        data class DeploymentEvent(
            val eventId: String,
            val databaseName: String,
            val designDocId: String,
            val action: DeploymentAction,
            val success: Boolean,
            val details: String,
            val timestamp: Instant = Clock.System.now()
        )

        enum class DeploymentAction {
            CREATE, UPDATE, DELETE, VALIDATE
        }

        interface ViewValidator {
            suspend fun validateView(viewDefinition: ViewDefinition): ValidationResult
        }

        @Serializable
        data class ValidationResult(
            val isValid: Boolean,
            val errors: List<String> = emptyList(),
            val warnings: List<String> = emptyList()
        )

        suspend fun deployDesignDocument(
            databaseName: String,
            designDoc: DesignDocument
        ): DeploymentResult {
            return try {
                // Validate design document
                val validation = validateDesignDocument(designDoc)
                if (!validation.isValid) {
                    return DeploymentResult.Error("Validation failed: ${validation.errors.joinToString(", ")}")
                }
                
                // Deploy to CouchDB
                val response = couchClient.putDesignDocument(databaseName, designDoc)
                
                if (response.ok) {
                    // Cache the design document
                    designDocumentCache["$databaseName:${designDoc.id}"] = CachedDesignDocument(
                        designDoc = designDoc,
                        timestamp = Clock.System.now(),
                        version = response.rev ?: "unknown"
                    )
                    
                    // Record deployment event
                    deploymentHistory.add(
                        DeploymentEvent(
                            eventId = generateEventId(),
                            databaseName = databaseName,
                            designDocId = designDoc.id,
                            action = DeploymentAction.CREATE,
                            success = true,
                            details = "Design document deployed successfully"
                        )
                    )
                    
                    DeploymentResult.Success(response)
                } else {
                    DeploymentResult.Error("Failed to deploy design document")
                }
            } catch (e: Exception) {
                DeploymentResult.Error(e.message ?: "Unknown error")
            }
        }

        private suspend fun validateDesignDocument(designDoc: DesignDocument): ValidationResult {
            val errors = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Validate views
            designDoc.views.forEach { (viewName, viewDefinition) ->
                viewValidators.forEach { validator ->
                    val validation = validator.validateView(viewDefinition)
                    errors.addAll(validation.errors.map { "View '$viewName': $it" })
                    warnings.addAll(validation.warnings.map { "View '$viewName': $it" })
                }
            }
            
            return ValidationResult(
                isValid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )
        }

        suspend fun addViewValidator(validator: ViewValidator) {
            viewValidators.add(validator)
        }

        fun getDesignDocument(
            databaseName: String,
            designDocId: String
        ): CachedDesignDocument? {
            return designDocumentCache["$databaseName:$designDocId"]
        }

        fun getDeploymentHistory(
            databaseName: String? = null,
            designDocId: String? = null,
            limit: Int = 100
        ): List<DeploymentEvent> {
            return deploymentHistory
                .filter { event ->
                    (databaseName == null || event.databaseName == databaseName) &&
                    (designDocId == null || event.designDocId == designDocId)
                }
                .sortedBy { it.timestamp }
                .takeLast(limit)
        }

        private fun generateEventId(): String {
            return "design_${agentId}_${Clock.System.now().toEpochMilliseconds()}"
        }

        sealed class DeploymentResult {
            data class Success(val response: CouchResponse) : DeploymentResult()
            data class Error(val message: String) : DeploymentResult()
        }
    }
} 