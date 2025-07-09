package fiduciary.workers

import fiduciary.agents.FiduciaryAgentSystem
import borg.trikeshed.dht.kademlia.id.NUID
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class NexusProcessAnalysisTest {

    private val mockAgentSystem = object : FiduciaryAgentSystem(NUID("mockAgentSystem")) {}
    private val analyzer = NexusProcessAnalysis(NUID("testAnalyzer"), mockAgentSystem)

    @Test
    fun testAnalyzeProcessBasicFlow() = runTest {
        val request = NexusProcessAnalysis.ProcessAnalysisRequest(
            requestId = "REQ1",
            processType = NexusProcessAnalysis.InternalProcessType.FIDUCIARY_ATTENTION,
            analysisCapabilities = setOf(NexusProcessAnalysis.ProcessAnalysisCapability.INTERNAL_FLOW_ANALYSIS),
            targetMetrics = setOf(NexusProcessAnalysis.PerformanceMetric.THROUGHPUT),
            timeWindow = 1.hours
        )

        val result = analyzer.performAnalysis(request)
        assertNotNull(result)
        assertEquals("REQ1", result.requestId)
        assertEquals(NexusProcessAnalysis.InternalProcessType.FIDUCIARY_ATTENTION, result.processType)
        assertTrue(result.findings.isNotEmpty())
        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.performanceMetrics.isNotEmpty())
        assertEquals(1000.0, result.performanceMetrics[NexusProcessAnalysis.PerformanceMetric.THROUGHPUT])
    }

    @Test
    fun testDetectBottlenecks() = runTest {
        val request = NexusProcessAnalysis.ProcessAnalysisRequest(
            requestId = "REQ2",
            processType = NexusProcessAnalysis.InternalProcessType.AGENT_COORDINATION,
            analysisCapabilities = setOf(NexusProcessAnalysis.ProcessAnalysisCapability.BOTTLENECK_DETECTION),
            targetMetrics = emptySet(),
            timeWindow = 1.hours
        )

        val result = analyzer.performAnalysis(request)
        assertNotNull(result)
        val bottleneckFinding = result.findings.find { it.type == NexusProcessAnalysis.FindingType.BOTTLENECK }
        assertNotNull(bottleneckFinding)
        assertEquals(NexusProcessAnalysis.FindingSeverity.MEDIUM, bottleneckFinding.severity)
    }

    @Test
    fun testGenerateRecommendationsForBottleneck() = runTest {
        val findings = listOf(
            NexusProcessAnalysis.ProcessFinding(
                type = NexusProcessAnalysis.FindingType.BOTTLENECK,
                severity = NexusProcessAnalysis.FindingSeverity.HIGH,
                description = "Agent coordination shows message queuing bottleneck",
                impactAssessment = NexusProcessAnalysis.ImpactAssessment(0.6, 0.4, 0.7, 0.6),
                evidence = listOf("Message queue depth", "Coordination latency")
            )
        )
        val optimizationEngine = analyzer.OptimizationEngine()
        val recommendations = optimizationEngine.generateRecommendations(findings, NexusProcessAnalysis.InternalProcessType.AGENT_COORDINATION)

        assertEquals(1, recommendations.size)
        assertEquals(NexusProcessAnalysis.OptimizationType.LOAD_BALANCING, recommendations[0].type)
        assertEquals(1, recommendations[0].priority)
    }

    @Test
    fun testPerformanceMonitoring() = runTest {
        val request = NexusProcessAnalysis.ProcessAnalysisRequest(
            requestId = "REQ3",
            processType = NexusProcessAnalysis.InternalProcessType.RESOURCE_ALLOCATION,
            analysisCapabilities = emptySet(),
            targetMetrics = setOf(
                NexusProcessAnalysis.PerformanceMetric.LATENCY,
                NexusProcessAnalysis.PerformanceMetric.CPU_UTILIZATION
            ),
            timeWindow = 1.hours
        )

        val result = analyzer.performAnalysis(request)
        assertNotNull(result)
        assertEquals(200.0, result.performanceMetrics[NexusProcessAnalysis.PerformanceMetric.LATENCY])
        assertEquals(0.60, result.performanceMetrics[NexusProcessAnalysis.PerformanceMetric.CPU_UTILIZATION])
    }

    @Test
    fun testConfidenceCalculation() = runTest {
        val findings = listOf(
            NexusProcessAnalysis.ProcessFinding(
                type = NexusProcessAnalysis.FindingType.BOTTLENECK,
                severity = NexusProcessAnalysis.FindingSeverity.HIGH,
                description = "",
                impactAssessment = NexusProcessAnalysis.ImpactAssessment(0.0, 0.0, 0.0, 0.0),
                evidence = emptyList()
            )
        )
        val metrics = mapOf(
            NexusProcessAnalysis.PerformanceMetric.THROUGHPUT to 100.0,
            NexusProcessAnalysis.PerformanceMetric.LATENCY to 50.0
        )

        // Directly call the private function for testing purposes
        val calculateConfidenceMethod = NexusProcessAnalysis::class.java.getDeclaredMethod(
            "calculateConfidence",
            List::class.java,
            Map::class.java
        ).apply { isAccessible = true }

        val confidence = calculateConfidenceMethod.invoke(analyzer, findings, metrics) as Double
        // Expected: baseConfidence (0.7) + findingsWeight (1 * 0.1) + metricsWeight (2 * 0.15) = 0.7 + 0.1 + 0.3 = 1.1. Coerced to 1.0
        assertEquals(1.0, confidence, 0.001)
    }
}
