package nexus.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests demonstrating the generated DSL for DefaultNexusAgent.
 * 
 * This showcases the "ffmpeg-like" configuration pattern where:
 * - Every property becomes a chainable method
 * - Complex types get nested builder blocks
 * - Collections get specialized add/remove methods
 * - Validation is built-in where appropriate
 */
class DefaultNexusAgentDslTest {

    @Test
    fun `should create agent with basic configuration using DSL`() {
        // Using the generated DSL - clean, declarative configuration
        val agent = defaultNexusAgent {
            nodeId("test-agent-001")
            networkId("nexus-testnet")
            heartbeatIntervalMs(15000)
            maxConcurrentTasks(5)
            logLevel(LogLevel.DEBUG)
        }

        assertEquals("test-agent-001", agent.nodeId)
        assertEquals("nexus-testnet", agent.networkId)
        assertEquals(15000L, agent.heartbeatIntervalMs)
        assertEquals(5, agent.maxConcurrentTasks)
        assertEquals(LogLevel.DEBUG, agent.logLevel)
    }

    @Test
    fun `should configure agent capabilities using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("capability-test")
            capability(AgentCapability.GOSSIP)
            capability(AgentCapability.TELEMETRY)
            capability(AgentCapability.AI_REASONING)
            capability(AgentCapability.PLUGIN_MANAGEMENT)
        }

        assertEquals(4, agent.capabilities.size)
        assertTrue(agent.capabilities.contains(AgentCapability.GOSSIP))
        assertTrue(agent.capabilities.contains(AgentCapability.TELEMETRY))
        assertTrue(agent.capabilities.contains(AgentCapability.AI_REASONING))
        assertTrue(agent.capabilities.contains(AgentCapability.PLUGIN_MANAGEMENT))
    }

    @Test
    fun `should configure workflows using nested DSL`() {
        val agent = defaultNexusAgent {
            nodeId("workflow-test")
            workflow {
                name("data-sync-workflow")
                description("Synchronizes data with peers")
                priority(10)
                timeoutMs(600000) // 10 minutes
                step {
                    id("discover-peers")
                    name("Peer Discovery")
                    action("discover_peers")
                    timeoutMs(30000)
                }
                step {
                    id("sync-data")
                    name("Data Synchronization")
                    action("sync_data")
                    parameter("batch_size", "1000")
                    parameter("compression", "gzip")
                }
                trigger(WorkflowTrigger.SCHEDULED)
                trigger(WorkflowTrigger.MESSAGE_RECEIVED)
            }
        }

        assertEquals(1, agent.workflows.size)
        val workflow = agent.workflows.first()
        assertEquals("data-sync-workflow", workflow.name)
        assertEquals("Synchronizes data with peers", workflow.description)
        assertEquals(10, workflow.priority)
        assertEquals(600000L, workflow.timeoutMs)
        assertEquals(2, workflow.steps.size)
        assertEquals(2, workflow.triggers.size)
        
        val firstStep = workflow.steps.first()
        assertEquals("discover-peers", firstStep.id)
        assertEquals("Peer Discovery", firstStep.name)
        assertEquals("discover_peers", firstStep.action)
        
        val secondStep = workflow.steps[1]
        assertEquals("sync-data", secondStep.id)
        assertEquals(2, secondStep.parameters.size)
        assertEquals("1000", secondStep.parameters["batch_size"])
        assertEquals("gzip", secondStep.parameters["compression"])
    }

    @Test
    fun `should configure plugins using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("plugin-test")
            plugin {
                name("telemetry-collector")
                version("1.2.3")
                config("interval", "5000")
                config("batch_size", "100")
                dependency("metrics-core")
                dependency("prometheus-client")
            }
            plugin {
                name("ai-reasoning-engine")
                version("2.0.0")
                config("model", "gpt-4")
                config("temperature", "0.7")
            }
        }

        assertEquals(2, agent.plugins.size)
        
        val telemetryPlugin = agent.plugins.find { it.name == "telemetry-collector" }
        assertNotNull(telemetryPlugin)
        assertEquals("1.2.3", telemetryPlugin.version)
        assertEquals(2, telemetryPlugin.config.size)
        assertEquals("5000", telemetryPlugin.config["interval"])
        assertEquals(2, telemetryPlugin.dependencies.size)
        
        val aiPlugin = agent.plugins.find { it.name == "ai-reasoning-engine" }
        assertNotNull(aiPlugin)
        assertEquals("2.0.0", aiPlugin.version)
        assertEquals("gpt-4", aiPlugin.config["model"])
    }

    @Test
    fun `should configure security settings using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("security-test")
            authToken("secret-token-123")
            enableEncryption(true)
            allowedPeer("peer-001")
            allowedPeer("peer-002")
            allowedPeer("peer-003")
        }

        assertEquals("secret-token-123", agent.authToken)
        assertTrue(agent.enableEncryption)
        assertEquals(3, agent.allowedPeers.size)
        assertTrue(agent.allowedPeers.contains("peer-001"))
        assertTrue(agent.allowedPeers.contains("peer-002"))
        assertTrue(agent.allowedPeers.contains("peer-003"))
    }

    @Test
    fun `should configure monitoring settings using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("monitoring-test")
            enableMetrics(true)
            metricsIntervalMs(10000)
            logLevel(LogLevel.TRACE)
        }

        assertTrue(agent.enableMetrics)
        assertEquals(10000L, agent.metricsIntervalMs)
        assertEquals(LogLevel.TRACE, agent.logLevel)
    }

    @Test
    fun `should configure custom settings using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("custom-test")
            customConfig("feature_flag_ai", "true")
            customConfig("cache_size", "1024")
            customConfig("debug_mode", "enabled")
        }

        assertEquals(3, agent.customConfig.size)
        assertEquals("true", agent.customConfig["feature_flag_ai"])
        assertEquals("1024", agent.customConfig["cache_size"])
        assertEquals("enabled", agent.customConfig["debug_mode"])
    }

    @Test
    fun `should configure gossip topics using DSL`() {
        val agent = defaultNexusAgent {
            nodeId("gossip-test")
            gossipTopic("nexus/custom-topic")
            gossipTopic("nexus/events")
            gossipTopic("nexus/alerts")
        }

        assertEquals(3, agent.gossipTopics.size)
        assertTrue(agent.gossipTopics.contains("nexus/custom-topic"))
        assertTrue(agent.gossipTopics.contains("nexus/events"))
        assertTrue(agent.gossipTopics.contains("nexus/alerts"))
    }

    @Test
    fun `should demonstrate complex nested configuration`() {
        val agent = defaultNexusAgent {
            nodeId("complex-test")
            networkId("nexus-production")
            
            // Configure multiple workflows
            workflow {
                name("health-check")
                description("Periodic health monitoring")
                priority(1)
                step {
                    id("ping-peers")
                    name("Peer Health Check")
                    action("ping_peers")
                }
                trigger(WorkflowTrigger.SCHEDULED)
            }
            
            workflow {
                name("data-backup")
                description("Backup critical data")
                priority(5)
                step {
                    id("backup-db")
                    name("Database Backup")
                    action("backup_database")
                    parameter("format", "sqlite")
                }
                trigger(WorkflowTrigger.SCHEDULED)
            }
            
            // Configure plugins
            plugin {
                name("health-monitor")
                version("1.0.0")
                config("check_interval", "30000")
            }
            
            // Configure capabilities
            capability(AgentCapability.GOSSIP)
            capability(AgentCapability.TELEMETRY)
            capability(AgentCapability.TASK_EXECUTION)
            
            // Configure security
            authToken("prod-token-xyz")
            enableEncryption(true)
            allowedPeer("prod-peer-001")
            
            // Configure monitoring
            logLevel(LogLevel.INFO)
            enableMetrics(true)
            metricsIntervalMs(15000)
        }

        // Verify complex configuration
        assertEquals("complex-test", agent.nodeId)
        assertEquals("nexus-production", agent.networkId)
        assertEquals(2, agent.workflows.size)
        assertEquals(1, agent.plugins.size)
        assertEquals(3, agent.capabilities.size)
        assertEquals("prod-token-xyz", agent.authToken)
        assertTrue(agent.enableEncryption)
        assertEquals(1, agent.allowedPeers.size)
        assertEquals(LogLevel.INFO, agent.logLevel)
        assertTrue(agent.enableMetrics)
        assertEquals(15000L, agent.metricsIntervalMs)
    }

    @Test
    fun `should demonstrate gossip functionality with DSL-configured agent`() = runBlocking {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("gossip-test-agent")
            networkId("test-network")
        }

        val payload: GossipPayload = _i(
            "status" j "online",
            "timestamp" j "2024-01-15T10:30:00Z",
            "metrics" j "cpu:45,memory:60"
        )

        agent.gossipAbout("nexus/test-gossip", payload)

        assertEquals(1, testIpfsService.publications.size)
        val (topic, message) = testIpfsService.publications.first()
        assertEquals("nexus/test-gossip", topic)
        
        // Verify the serialized message contains our payload
        assertTrue(message.contains("status"))
        assertTrue(message.contains("online"))
        assertTrue(message.contains("timestamp"))
        assertTrue(message.contains("metrics"))
    }
} 