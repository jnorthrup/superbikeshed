package nexus.demo

import nexus.core.*
import kotlinx.coroutines.runBlocking

/**
 * Demo application showcasing the DefaultNexusAgent DSL.
 * 
 * This demonstrates the "ffmpeg-like" configuration pattern where:
 * - Every property becomes a chainable method
 * - Complex types get nested builder blocks  
 * - Collections get specialized add/remove methods
 * - Validation is built-in where appropriate
 * 
 * The generated DSL makes configuration clean, declarative, and self-documenting.
 */
fun main() = runBlocking {
    println("=== TrikeShed Nexus Agent DSL Demo ===")
    println()
    
    // Create a test IPFS service for the demo
    val testIpfsService = TestIpfsPubSubService()
    
    // Example 1: Basic agent configuration
    println("1. Basic Agent Configuration:")
    val basicAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("demo-agent-001")
        networkId("nexus-demo")
        heartbeatIntervalMs(10000)
        logLevel(LogLevel.INFO)
    }
    println("   Created agent: ${basicAgent.nodeId} on network: ${basicAgent.networkId}")
    println()
    
    // Example 2: Agent with workflows
    println("2. Agent with Workflows:")
    val workflowAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("workflow-agent")
        networkId("nexus-demo")
        
        // Configure a data synchronization workflow
        workflow {
            name("data-sync")
            description("Synchronizes data with peer nodes")
            priority(5)
            timeoutMs(300000) // 5 minutes
            
            step {
                id("discover")
                name("Peer Discovery")
                action("discover_peers")
                timeoutMs(30000)
            }
            
            step {
                id("sync")
                name("Data Sync")
                action("sync_data")
                parameter("batch_size", "500")
                parameter("compression", "lz4")
                timeoutMs(120000)
            }
            
            step {
                id("verify")
                name("Verification")
                action("verify_sync")
                condition("sync.success == true")
                timeoutMs(60000)
            }
            
            trigger(WorkflowTrigger.SCHEDULED)
            trigger(WorkflowTrigger.MESSAGE_RECEIVED)
        }
        
        // Configure a health monitoring workflow
        workflow {
            name("health-monitor")
            description("Monitors system health and reports issues")
            priority(1)
            
            step {
                id("check")
                name("Health Check")
                action("check_health")
                parameter("timeout", "5000")
            }
            
            step {
                id("report")
                name("Report Status")
                action("report_status")
                condition("check.healthy == true")
            }
            
            trigger(WorkflowTrigger.SCHEDULED)
        }
    }
    println("   Created agent with ${workflowAgent.workflows.size} workflows:")
    workflowAgent.workflows.forEach { workflow ->
        println("     - ${workflow.name}: ${workflow.description}")
        println("       Steps: ${workflow.steps.size}, Priority: ${workflow.priority}")
    }
    println()
    
    // Example 3: Agent with plugins
    println("3. Agent with Plugins:")
    val pluginAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("plugin-agent")
        networkId("nexus-demo")
        
        plugin {
            name("telemetry-collector")
            version("1.2.3")
            config("interval", "5000")
            config("batch_size", "100")
            config("retention_days", "30")
            dependency("metrics-core")
            dependency("prometheus-client")
        }
        
        plugin {
            name("ai-reasoning-engine")
            version("2.0.0")
            config("model", "gpt-4")
            config("temperature", "0.7")
            config("max_tokens", "2048")
            dependency("openai-client")
            dependency("vector-db")
        }
        
        plugin {
            name("security-monitor")
            version("1.0.0")
            config("scan_interval", "60000")
            config("threat_level", "medium")
            dependency("security-core")
        }
    }
    println("   Created agent with ${pluginAgent.plugins.size} plugins:")
    pluginAgent.plugins.forEach { plugin ->
        println("     - ${plugin.name} v${plugin.version}")
        println("       Config: ${plugin.config.size} items, Dependencies: ${plugin.dependencies.size}")
    }
    println()
    
    // Example 4: Agent with security configuration
    println("4. Agent with Security Configuration:")
    val secureAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("secure-agent")
        networkId("nexus-production")
        
        // Security settings
        authToken("prod-secret-token-xyz123")
        enableEncryption(true)
        allowedPeer("prod-peer-001")
        allowedPeer("prod-peer-002")
        allowedPeer("prod-peer-003")
        
        // Capabilities
        capability(AgentCapability.GOSSIP)
        capability(AgentCapability.TELEMETRY)
        capability(AgentCapability.TASK_EXECUTION)
        capability(AgentCapability.AI_REASONING)
        
        // Monitoring
        logLevel(LogLevel.INFO)
        enableMetrics(true)
        metricsIntervalMs(15000)
    }
    println("   Created secure agent:")
    println("     - Encryption: ${secureAgent.enableEncryption}")
    println("     - Allowed peers: ${secureAgent.allowedPeers.size}")
    println("     - Capabilities: ${secureAgent.capabilities.size}")
    println("     - Auth token: ${secureAgent.authToken?.take(10)}...")
    println()
    
    // Example 5: Complex production configuration
    println("5. Complex Production Configuration:")
    val productionAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("prod-agent-main")
        networkId("nexus-production")
        
        // Network settings
        heartbeatIntervalMs(30000)
        maxMessageSize(2 * 1024 * 1024) // 2MB
        maxConcurrentTasks(20)
        taskTimeoutMs(120000) // 2 minutes
        retryAttempts(5)
        
        // Gossip topics
        gossipTopic("nexus/production/events")
        gossipTopic("nexus/production/alerts")
        gossipTopic("nexus/production/metrics")
        gossipTopic("nexus/production/health")
        
        // Workflows
        workflow {
            name("production-monitor")
            description("Comprehensive production monitoring")
            priority(1)
            timeoutMs(600000) // 10 minutes
            
            step {
                id("collect-metrics")
                name("Collect Metrics")
                action("collect_system_metrics")
                parameter("interval", "5000")
            }
            
            step {
                id("analyze-health")
                name("Health Analysis")
                action("analyze_health")
                parameter("threshold", "0.8")
            }
            
            step {
                id("alert-if-needed")
                name("Alert Generation")
                action("generate_alerts")
                condition("analyze-health.status == 'critical'")
            }
            
            trigger(WorkflowTrigger.SCHEDULED)
        }
        
        // Plugins
        plugin {
            name("production-monitor")
            version("2.1.0")
            config("alert_threshold", "0.9")
            config("retention_days", "90")
        }
        
        plugin {
            name("ai-optimizer")
            version("1.5.0")
            config("optimization_interval", "300000")
            config("learning_rate", "0.01")
        }
        
        // Security
        authToken("prod-secure-token-abc789")
        enableEncryption(true)
        allowedPeer("prod-node-001")
        allowedPeer("prod-node-002")
        allowedPeer("prod-node-003")
        allowedPeer("prod-node-004")
        
        // Capabilities
        capability(AgentCapability.GOSSIP)
        capability(AgentCapability.TELEMETRY)
        capability(AgentCapability.TASK_EXECUTION)
        capability(AgentCapability.WORKFLOW_MANAGEMENT)
        capability(AgentCapability.PEER_DISCOVERY)
        capability(AgentCapability.DATA_SYNC)
        capability(AgentCapability.AI_REASONING)
        capability(AgentCapability.PLUGIN_MANAGEMENT)
        
        // Monitoring
        logLevel(LogLevel.INFO)
        enableMetrics(true)
        metricsIntervalMs(10000)
        
        // Custom configuration
        customConfig("environment", "production")
        customConfig("region", "us-west-2")
        customConfig("instance_type", "c5.2xlarge")
        customConfig("auto_scaling", "enabled")
    }
    
    println("   Created production agent:")
    println("     - Node ID: ${productionAgent.nodeId}")
    println("     - Network: ${productionAgent.networkId}")
    println("     - Workflows: ${productionAgent.workflows.size}")
    println("     - Plugins: ${productionAgent.plugins.size}")
    println("     - Capabilities: ${productionAgent.capabilities.size}")
    println("     - Gossip topics: ${productionAgent.gossipTopics.size}")
    println("     - Custom config: ${productionAgent.customConfig.size} items")
    println()
    
    // Example 6: Demonstrate gossip functionality
    println("6. Gossip Functionality Demo:")
    val gossipAgent = defaultNexusAgent {
        ipfsPubSubService(testIpfsService)
        nodeId("gossip-demo")
        networkId("nexus-demo")
    }
    
    // Publish some gossip messages
    val statusPayload: GossipPayload = _i(
        "status" j "online",
        "timestamp" j "2024-01-15T10:30:00Z",
        "cpu_usage" j "45.2",
        "memory_usage" j "67.8",
        "disk_usage" j "23.1"
    )
    
    val alertPayload: GossipPayload = _i(
        "alert_type" j "high_cpu",
        "severity" j "warning",
        "message" j "CPU usage above 80%",
        "timestamp" j "2024-01-15T10:31:00Z"
    )
    
    gossipAgent.gossipAbout("nexus/demo/status", statusPayload)
    gossipAgent.gossipAbout("nexus/demo/alerts", alertPayload)
    
    println("   Published ${testIpfsService.publications.size} gossip messages:")
    testIpfsService.publications.forEach { (topic, message) ->
        println("     - Topic: $topic")
        println("       Message: ${message.take(100)}...")
    }
    println()
    
    println("=== Demo Complete ===")
    println()
    println("The DSL provides:")
    println("- Clean, declarative configuration")
    println("- Type-safe property setting")
    println("- Nested builders for complex types")
    println("- Collection helpers for lists/sets")
    println("- Built-in validation")
    println("- Self-documenting API")
    println()
    println("This demonstrates the 'ffmpeg-like' configuration pattern")
    println("where every aspect of the system can be configured through")
    println("a fluent, chainable API that's both powerful and intuitive.")
} 