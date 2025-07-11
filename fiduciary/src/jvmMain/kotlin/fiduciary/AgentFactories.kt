package fiduciary

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.milliseconds

/**
 * Agent Factories for Concentric Dispatch
 * 
 * Creates actual working agents that perform real tasks:
 * - Network analysis agents
 * - Risk assessment agents  
 * - Pattern detection agents
 * - Consensus building agents
 * - Data validation agents
 */

// Base Agent Interface
interface WorkingAgent {
    val id: String
    val capabilities: Set<TaskType>
    val ring: ConcentricRing
    suspend fun processTask(task: DispatchTask): DispatchResult
    suspend fun start(scope: CoroutineScope)
    suspend fun stop()
    fun getStatus(): AgentStatus
}

data class AgentStatus(
    val id: String,
    val isActive: Boolean,
    val currentLoad: Int,
    val tasksProcessed: Long,
    val successRate: Double,
    val averageProcessingTime: Long
)

// Core Agent - Handles critical consensus and coordination
class CoreAgent(
    override val id: String
) : WorkingAgent, CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<CoreAgent>
    override val key: CoroutineContext.Key<*> get() = Key
    
    override val capabilities = setOf(TaskType.CONSENSUS, TaskType.AGGREGATE)
    override val ring = ConcentricRing.CORE
    
    private var isRunning = false
    private var tasksProcessed = 0L
    private var totalProcessingTime = 0L
    private var successfulTasks = 0L
    
    override suspend fun start(scope: CoroutineScope) {
        isRunning = true
        println("🔥 Core Agent $id starting - Critical decision making active")
    }
    
    override suspend fun processTask(task: DispatchTask): DispatchResult {
        val startTime = System.currentTimeMillis()
        
        println("🔥 Core Agent $id: Processing critical task ${task.id}")
        
        // Core agents handle the most critical decisions
        val result = when (task.type) {
            TaskType.CONSENSUS -> buildCriticalConsensus(task)
            TaskType.AGGREGATE -> aggregateCriticalData(task)
            else -> {
                println("🔥 Core Agent $id: Escalating non-critical task to appropriate ring")
                return DispatchResult(
                    taskId = task.id,
                    agentId = id,
                    ring = ring,
                    result = null,
                    status = ResultStatus.FAILED,
                    processingTime = 0,
                    confidence = 0.0
                )
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        tasksProcessed++
        totalProcessingTime += processingTime
        if (result != null) successfulTasks++
        
        return DispatchResult(
            taskId = task.id,
            agentId = id,
            ring = ring,
            result = result,
            status = if (result != null) ResultStatus.SUCCESS else ResultStatus.FAILED,
            processingTime = processingTime,
            confidence = 0.98 // Core agents have highest confidence
        )
    }
    
    private suspend fun buildCriticalConsensus(task: DispatchTask): FiduciaryData? {
        // Critical consensus building - highest priority decisions
        delay(200.milliseconds) // Simulate careful analysis
        
        val payload = task.payload
        return payload.copy(
            stage = "critical_consensus",
            content = payload.content + mapOf(
                "consensus_level" to "critical",
                "decision_authority" to "core_agent",
                "confidence_score" to 0.98,
                "escalation_level" to "executive",
                "processed_by" to id,
                "core_decision" to true
            )
        )
    }
    
    private suspend fun aggregateCriticalData(task: DispatchTask): FiduciaryData? {
        delay(150.milliseconds)
        
        val payload = task.payload
        return payload.copy(
            stage = "critical_aggregation",
            content = payload.content + mapOf(
                "aggregation_type" to "critical",
                "data_quality" to "executive_grade",
                "summary_confidence" to 0.95,
                "processed_by" to id
            )
        )
    }
    
    override suspend fun stop() {
        isRunning = false
        println("🔥 Core Agent $id stopped")
    }
    
    override fun getStatus(): AgentStatus {
        return AgentStatus(
            id = id,
            isActive = isRunning,
            currentLoad = 0,
            tasksProcessed = tasksProcessed,
            successRate = if (tasksProcessed > 0) successfulTasks.toDouble() / tasksProcessed else 0.0,
            averageProcessingTime = if (tasksProcessed > 0) totalProcessingTime / tasksProcessed else 0
        )
    }
}

// Validation Agent - Handles data validation and verification
class ValidationAgent(
    override val id: String,
    override val ring: ConcentricRing
) : WorkingAgent {
    
    override val capabilities = setOf(TaskType.VALIDATE, TaskType.PERCOLATE)
    
    private var isRunning = false
    private var tasksProcessed = 0L
    private var totalProcessingTime = 0L
    private var successfulTasks = 0L
    
    override suspend fun start(scope: CoroutineScope) {
        isRunning = true
        println("✅ Validation Agent $id starting in ${ring.name} ring")
    }
    
    override suspend fun processTask(task: DispatchTask): DispatchResult {
        val startTime = System.currentTimeMillis()
        
        println("✅ Validation Agent $id: Validating ${task.id}")
        
        val result = when (task.type) {
            TaskType.VALIDATE -> validateNetworkData(task)
            TaskType.PERCOLATE -> percolateData(task)
            else -> null
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        tasksProcessed++
        totalProcessingTime += processingTime
        if (result != null) successfulTasks++
        
        return DispatchResult(
            taskId = task.id,
            agentId = id,
            ring = ring,
            result = result,
            status = if (result != null) ResultStatus.SUCCESS else ResultStatus.FAILED,
            processingTime = processingTime,
            confidence = 0.85 + (ring.priority * 0.02) // Higher rings have higher confidence
        )
    }
    
    private suspend fun validateNetworkData(task: DispatchTask): FiduciaryData? {
        delay((50..150).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        // Perform actual validation
        val validations = mutableMapOf<String, Any>()
        
        // Validate IP address format
        val target = content["target"]?.toString()
        validations["ip_format_valid"] = target?.matches(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")) ?: false
        
        // Validate port range
        val port = content["port"] as? Int
        validations["port_valid"] = port != null && port in 1..65535
        
        // Validate service consistency
        val service = content["service"]?.toString()
        validations["service_port_consistent"] = when {
            port == 22 && service == "ssh" -> true
            port == 80 && service == "http" -> true
            port == 443 && service == "https" -> true
            port == 3389 && service == "rdp" -> true
            else -> false
        }
        
        // Calculate validation score
        val validationScore = validations.values.count { it == true }.toDouble() / validations.size
        
        return payload.copy(
            stage = "validated",
            content = payload.content + mapOf(
                "validation_score" to validationScore,
                "validations" to validations,
                "validated_by" to id,
                "validation_timestamp" to System.currentTimeMillis(),
                "validation_ring" to ring.name
            )
        )
    }
    
    private suspend fun percolateData(task: DispatchTask): FiduciaryData? {
        delay((30..100).random().milliseconds)
        
        val payload = task.payload
        return payload.copy(
            stage = "percolated",
            content = payload.content + mapOf(
                "percolated_by" to id,
                "percolation_ring" to ring.name,
                "percolation_timestamp" to System.currentTimeMillis()
            )
        )
    }
    
    override suspend fun stop() {
        isRunning = false
        println("✅ Validation Agent $id stopped")
    }
    
    override fun getStatus(): AgentStatus {
        return AgentStatus(
            id = id,
            isActive = isRunning,
            currentLoad = 0,
            tasksProcessed = tasksProcessed,
            successRate = if (tasksProcessed > 0) successfulTasks.toDouble() / tasksProcessed else 0.0,
            averageProcessingTime = if (tasksProcessed > 0) totalProcessingTime / tasksProcessed else 0
        )
    }
}

// Analysis Agent - Handles pattern detection and risk analysis
class AnalysisAgent(
    override val id: String,
    override val ring: ConcentricRing
) : WorkingAgent {
    
    override val capabilities = setOf(TaskType.ANALYZE_PATTERNS, TaskType.DETECT_ANOMALIES, TaskType.CLASSIFY)
    
    private var isRunning = false
    private var tasksProcessed = 0L
    private var totalProcessingTime = 0L
    private var successfulTasks = 0L
    
    override suspend fun start(scope: CoroutineScope) {
        isRunning = true
        println("🔍 Analysis Agent $id starting in ${ring.name} ring")
    }
    
    override suspend fun processTask(task: DispatchTask): DispatchResult {
        val startTime = System.currentTimeMillis()
        
        println("🔍 Analysis Agent $id: Analyzing ${task.id}")
        
        val result = when (task.type) {
            TaskType.ANALYZE_PATTERNS -> analyzeNetworkPatterns(task)
            TaskType.DETECT_ANOMALIES -> detectNetworkAnomalies(task)
            TaskType.CLASSIFY -> classifyThreatLevel(task)
            else -> null
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        tasksProcessed++
        totalProcessingTime += processingTime
        if (result != null) successfulTasks++
        
        return DispatchResult(
            taskId = task.id,
            agentId = id,
            ring = ring,
            result = result,
            status = if (result != null) ResultStatus.SUCCESS else ResultStatus.FAILED,
            processingTime = processingTime,
            confidence = 0.75 + (ring.priority * 0.03)
        )
    }
    
    private suspend fun analyzeNetworkPatterns(task: DispatchTask): FiduciaryData? {
        delay((100..300).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        val patterns = mutableListOf<String>()
        
        // Analyze attack patterns
        val port = content["port"] as? Int
        val service = content["service"]?.toString()
        val isExposed = content["exposed"] as? Boolean ?: false
        val isVulnerable = content["vulnerable"] as? Boolean ?: false
        
        if (port == 22 && isExposed) patterns.add("ssh_exposure")
        if (port == 3389 && isExposed) patterns.add("rdp_exposure")
        if (port in listOf(5432, 5984, 6379) && isExposed) patterns.add("database_exposure")
        if (isVulnerable && isExposed) patterns.add("vulnerable_service_exposed")
        if (content["ssl_expired"] == true) patterns.add("certificate_expiration")
        
        // Time-based patterns
        val timestamp = System.currentTimeMillis()
        val hour = (timestamp / (1000 * 60 * 60)) % 24
        if (hour in 22..6) patterns.add("off_hours_activity")
        
        return payload.copy(
            stage = "pattern_analyzed",
            content = payload.content + mapOf(
                "detected_patterns" to patterns,
                "pattern_count" to patterns.size,
                "pattern_confidence" to if (patterns.isNotEmpty()) 0.8 else 0.2,
                "analyzed_by" to id,
                "analysis_ring" to ring.name
            )
        )
    }
    
    private suspend fun detectNetworkAnomalies(task: DispatchTask): FiduciaryData? {
        delay((150..400).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        val anomalies = mutableListOf<String>()
        var anomalyScore = 0.0
        
        // Port anomalies
        val port = content["port"] as? Int
        if (port != null) {
            when {
                port in 1..1023 -> { // Well-known ports
                    anomalyScore += 0.2
                }
                port in 49152..65535 -> { // Dynamic ports
                    anomalies.add("dynamic_port_usage")
                    anomalyScore += 0.4
                }
                port in listOf(31337, 12345, 54321) -> { // Common backdoor ports
                    anomalies.add("suspicious_port")
                    anomalyScore += 0.8
                }
            }
        }
        
        // Service anomalies
        val service = content["service"]?.toString()
        val target = content["target"]?.toString() ?: ""
        
        if (service == "unknown" && port in listOf(22, 80, 443)) {
            anomalies.add("known_port_unknown_service")
            anomalyScore += 0.6
        }
        
        // Geographic anomalies
        if (target.startsWith("203.0.113.")) { // TEST-NET-3
            anomalies.add("test_network_usage")
            anomalyScore += 0.3
        }
        
        // Timing anomalies
        val responseTime = content["response_time"] as? Int ?: 100
        if (responseTime > 5000) {
            anomalies.add("slow_response")
            anomalyScore += 0.3
        }
        
        return payload.copy(
            stage = "anomaly_detected",
            content = payload.content + mapOf(
                "anomalies" to anomalies,
                "anomaly_score" to anomalyScore,
                "anomaly_threshold" to 0.5,
                "is_anomalous" to (anomalyScore > 0.5),
                "detected_by" to id,
                "detection_ring" to ring.name
            )
        )
    }
    
    private suspend fun classifyThreatLevel(task: DispatchTask): FiduciaryData? {
        delay((80..200).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        var threatScore = 0.0
        val threats = mutableListOf<String>()
        
        // Base threat assessment
        val riskScore = content["risk_score"] as? Int ?: 0
        threatScore += riskScore / 100.0
        
        // Exposure-based threats
        if (content["exposed"] == true) {
            threats.add("exposure_threat")
            threatScore += 0.3
        }
        
        // Vulnerability-based threats
        if (content["vulnerable"] == true) {
            threats.add("vulnerability_threat")
            threatScore += 0.4
        }
        
        // Service-specific threats
        val port = content["port"] as? Int
        when (port) {
            22 -> {
                threats.add("ssh_access_risk")
                threatScore += 0.2
            }
            3389 -> {
                threats.add("rdp_access_risk")
                threatScore += 0.3
            }
            5432, 5984, 6379 -> {
                threats.add("database_access_risk")
                threatScore += 0.35
            }
        }
        
        // Pattern-based threats
        val patterns = content["detected_patterns"] as? List<*>
        if (patterns?.contains("vulnerable_service_exposed") == true) {
            threats.add("critical_exposure")
            threatScore += 0.5
        }
        
        val classification = when {
            threatScore >= 0.8 -> "critical"
            threatScore >= 0.6 -> "high"
            threatScore >= 0.4 -> "medium"
            threatScore >= 0.2 -> "low"
            else -> "minimal"
        }
        
        return payload.copy(
            stage = "threat_classified",
            content = payload.content + mapOf(
                "threat_score" to threatScore,
                "threat_classification" to classification,
                "identified_threats" to threats,
                "classification_confidence" to 0.85,
                "classified_by" to id,
                "classification_ring" to ring.name
            )
        )
    }
    
    override suspend fun stop() {
        isRunning = false
        println("🔍 Analysis Agent $id stopped")
    }
    
    override fun getStatus(): AgentStatus {
        return AgentStatus(
            id = id,
            isActive = isRunning,
            currentLoad = 0,
            tasksProcessed = tasksProcessed,
            successRate = if (tasksProcessed > 0) successfulTasks.toDouble() / tasksProcessed else 0.0,
            averageProcessingTime = if (tasksProcessed > 0) totalProcessingTime / tasksProcessed else 0
        )
    }
}

// Correlation Agent - Handles cross-referencing and correlation
class CorrelationAgent(
    override val id: String,
    override val ring: ConcentricRing
) : WorkingAgent {
    
    override val capabilities = setOf(TaskType.CORRELATE_FINDINGS, TaskType.ENRICH)
    
    private var isRunning = false
    private var tasksProcessed = 0L
    private var totalProcessingTime = 0L
    private var successfulTasks = 0L
    private val knowledgeBase = mutableMapOf<String, Any>()
    
    override suspend fun start(scope: CoroutineScope) {
        isRunning = true
        initializeKnowledgeBase()
        println("🔗 Correlation Agent $id starting in ${ring.name} ring")
    }
    
    private fun initializeKnowledgeBase() {
        // Initialize threat intelligence and correlation data
        knowledgeBase["known_attack_signatures"] = listOf(
            "ssh_brute_force", "rdp_brute_force", "sql_injection", 
            "xss_attempt", "directory_traversal", "buffer_overflow"
        )
        
        knowledgeBase["vulnerability_database"] = mapOf(
            "OpenSSH_7.4" to listOf("CVE-2018-15473", "CVE-2018-15919"),
            "Apache/2.2" to listOf("CVE-2017-7679", "CVE-2017-9788"),
            "nginx/1.14" to listOf("CVE-2019-9511", "CVE-2019-9513")
        )
        
        knowledgeBase["threat_actors"] = mapOf(
            "ssh_scanning" to "APT28",
            "rdp_exploitation" to "FIN7",
            "database_targeting" to "APT40"
        )
    }
    
    override suspend fun processTask(task: DispatchTask): DispatchResult {
        val startTime = System.currentTimeMillis()
        
        println("🔗 Correlation Agent $id: Correlating ${task.id}")
        
        val result = when (task.type) {
            TaskType.CORRELATE_FINDINGS -> correlateWithThreatIntel(task)
            TaskType.ENRICH -> enrichWithContextData(task)
            else -> null
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        tasksProcessed++
        totalProcessingTime += processingTime
        if (result != null) successfulTasks++
        
        return DispatchResult(
            taskId = task.id,
            agentId = id,
            ring = ring,
            result = result,
            status = if (result != null) ResultStatus.SUCCESS else ResultStatus.FAILED,
            processingTime = processingTime,
            confidence = 0.70 + (ring.priority * 0.04)
        )
    }
    
    private suspend fun correlateWithThreatIntel(task: DispatchTask): FiduciaryData? {
        delay((200..500).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        val correlations = mutableListOf<String>()
        var correlationScore = 0.0
        
        // Correlate with known vulnerabilities
        val version = content["version"]?.toString()
        if (version != null) {
            val vulnDb = knowledgeBase["vulnerability_database"] as? Map<String, List<String>>
            val vulnerabilities = vulnDb?.get(version)
            if (!vulnerabilities.isNullOrEmpty()) {
                correlations.add("known_vulnerabilities")
                correlationScore += 0.4
            }
        }
        
        // Correlate with attack patterns
        val patterns = content["detected_patterns"] as? List<*>
        val signatures = knowledgeBase["known_attack_signatures"] as? List<String>
        patterns?.forEach { pattern ->
            if (signatures?.contains(pattern.toString()) == true) {
                correlations.add("attack_signature_match")
                correlationScore += 0.3
            }
        }
        
        // Correlate with threat actors
        val service = content["service"]?.toString()
        val port = content["port"] as? Int
        val threatActors = knowledgeBase["threat_actors"] as? Map<String, String>
        
        when {
            service == "ssh" || port == 22 -> {
                threatActors?.get("ssh_scanning")?.let { actor ->
                    correlations.add("threat_actor_$actor")
                    correlationScore += 0.5
                }
            }
            service == "rdp" || port == 3389 -> {
                threatActors?.get("rdp_exploitation")?.let { actor ->
                    correlations.add("threat_actor_$actor")
                    correlationScore += 0.6
                }
            }
        }
        
        // Geographic correlation
        val target = content["target"]?.toString() ?: ""
        val geoCorrelation = when {
            target.startsWith("203.0.113.") -> "test_network"
            target.startsWith("192.168.") -> "internal_network"
            else -> "external_network"
        }
        
        return payload.copy(
            stage = "correlated",
            content = payload.content + mapOf(
                "correlations" to correlations,
                "correlation_score" to correlationScore,
                "threat_intelligence_match" to (correlationScore > 0.3),
                "geographic_correlation" to geoCorrelation,
                "correlated_by" to id,
                "correlation_ring" to ring.name,
                "knowledge_base_version" to "1.0"
            )
        )
    }
    
    private suspend fun enrichWithContextData(task: DispatchTask): FiduciaryData? {
        delay((100..250).random().milliseconds)
        
        val payload = task.payload
        val content = payload.content
        
        val enrichments = mutableMapOf<String, Any>()
        
        // Enrich with service information
        val port = content["port"] as? Int
        enrichments["service_category"] = when (port) {
            22 -> "remote_access"
            80, 443 -> "web_service"
            3389 -> "remote_desktop"
            5432 -> "database"
            5984 -> "document_store"
            6379 -> "cache_service"
            else -> "unknown"
        }
        
        // Enrich with business context
        val target = content["target"]?.toString() ?: ""
        enrichments["business_impact"] = when {
            target.startsWith("192.168.1.") -> "management_network"
            target.startsWith("192.168.2.") -> "production_network"
            target.startsWith("10.0.") -> "corporate_network"
            else -> "external"
        }
        
        // Enrich with compliance context
        enrichments["compliance_requirements"] = when (port) {
            22, 3389 -> listOf("SOX", "PCI_DSS", "HIPAA")
            80, 443 -> listOf("PCI_DSS", "GDPR")
            5432, 5984 -> listOf("SOX", "PCI_DSS", "HIPAA", "GDPR")
            else -> listOf("GENERAL")
        }
        
        // Enrich with time context
        val timestamp = System.currentTimeMillis()
        enrichments["business_hours"] = isBusinessHours(timestamp)
        enrichments["weekend"] = isWeekend(timestamp)
        
        return payload.copy(
            stage = "enriched",
            content = payload.content + enrichments + mapOf(
                "enriched_by" to id,
                "enrichment_ring" to ring.name,
                "enrichment_timestamp" to timestamp
            )
        )
    }
    
    private fun isBusinessHours(timestamp: Long): Boolean {
        val hour = (timestamp / (1000 * 60 * 60)) % 24
        return hour in 9..17
    }
    
    private fun isWeekend(timestamp: Long): Boolean {
        val dayOfWeek = ((timestamp / (1000 * 60 * 60 * 24)) + 4) % 7 // Thursday = 0
        return dayOfWeek in 5..6 // Saturday = 5, Sunday = 6
    }
    
    override suspend fun stop() {
        isRunning = false
        println("🔗 Correlation Agent $id stopped")
    }
    
    override fun getStatus(): AgentStatus {
        return AgentStatus(
            id = id,
            isActive = isRunning,
            currentLoad = 0,
            tasksProcessed = tasksProcessed,
            successRate = if (tasksProcessed > 0) successfulTasks.toDouble() / tasksProcessed else 0.0,
            averageProcessingTime = if (tasksProcessed > 0) totalProcessingTime / tasksProcessed else 0
        )
    }
}

// Agent Factory Registry
object AgentFactory {
    
    private val activeAgents = mutableMapOf<String, WorkingAgent>()
    
    suspend fun createAgentFleet(scope: CoroutineScope): Map<String, WorkingAgent> {
        println("🏭 Agent Factory: Creating agent fleet...")
        
        // Create Core agents
        val coreAgent = CoreAgent("core_001")
        activeAgents[coreAgent.id] = coreAgent
        coreAgent.start(scope)
        
        // Create Dyad agents (validation)
        repeat(2) { i ->
            val agent = ValidationAgent("dyad_${i.toString().padStart(3, '0')}", ConcentricRing.DYAD)
            activeAgents[agent.id] = agent
            agent.start(scope)
        }
        
        // Create Triad agents (validation + analysis)
        repeat(3) { i ->
            val agent = if (i == 0) {
                ValidationAgent("triad_${i.toString().padStart(3, '0')}", ConcentricRing.TRIAD)
            } else {
                AnalysisAgent("triad_${i.toString().padStart(3, '0')}", ConcentricRing.TRIAD)
            }
            activeAgents[agent.id] = agent
            agent.start(scope)
        }
        
        // Create Pentad agents (analysis focused)
        repeat(5) { i ->
            val agent = AnalysisAgent("pentad_${i.toString().padStart(3, '0')}", ConcentricRing.PENTAD)
            activeAgents[agent.id] = agent
            agent.start(scope)
        }
        
        // Create Dodecad agents (correlation and analysis)
        repeat(12) { i ->
            val agent = if (i < 6) {
                AnalysisAgent("dodecad_${i.toString().padStart(3, '0')}", ConcentricRing.DODECAD)
            } else {
                CorrelationAgent("dodecad_${i.toString().padStart(3, '0')}", ConcentricRing.DODECAD)
            }
            activeAgents[agent.id] = agent
            agent.start(scope)
        }
        
        // Create Senate agents (mixed capabilities)
        repeat(24) { i ->
            val agent = when (i % 4) {
                0 -> ValidationAgent("senate_${i.toString().padStart(3, '0')}", ConcentricRing.SENATE)
                1 -> AnalysisAgent("senate_${i.toString().padStart(3, '0')}", ConcentricRing.SENATE)
                2 -> CorrelationAgent("senate_${i.toString().padStart(3, '0')}", ConcentricRing.SENATE)
                else -> AnalysisAgent("senate_${i.toString().padStart(3, '0')}", ConcentricRing.SENATE)
            }
            activeAgents[agent.id] = agent
            agent.start(scope)
        }
        
        println("🏭 Agent Factory: Created ${activeAgents.size} working agents")
        println("   🔥 Core: 1 agent")
        println("   👥 Dyad: 2 agents") 
        println("   🔺 Triad: 3 agents")
        println("   ⭐ Pentad: 5 agents")
        println("   🌟 Dodecad: 12 agents")
        println("   🏛️ Senate: 24 agents")
        
        return activeAgents.toMap()
    }
    
    suspend fun stopAllAgents() {
        println("🏭 Agent Factory: Stopping all agents...")
        activeAgents.values.forEach { agent ->
            agent.stop()
        }
        activeAgents.clear()
        println("🏭 Agent Factory: All agents stopped")
    }
    
    fun getAgent(id: String): WorkingAgent? = activeAgents[id]
    
    fun getAgentsByRing(ring: ConcentricRing): List<WorkingAgent> {
        return activeAgents.values.filter { it.ring == ring }
    }
    
    fun getAgentsByCapability(capability: TaskType): List<WorkingAgent> {
        return activeAgents.values.filter { capability in it.capabilities }
    }
    
    fun getFleetStatus(): Map<ConcentricRing, List<AgentStatus>> {
        return ConcentricRing.values().associateWith { ring ->
            getAgentsByRing(ring).map { it.getStatus() }
        }
    }
}