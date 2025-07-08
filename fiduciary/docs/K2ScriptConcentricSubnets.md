# K2Script Sandboxing vs Concentric Subnet Task Networks

## The Mismatch

K2Script provides **process-level sandboxing** for individual agent execution, but this doesn't map cleanly to **concentric subnet task networks** where agents need layered, nested communication patterns.

## K2Script Sandboxing Model

```kotlin
// K2Script: One sandbox per agent
class K2ScriptSandbox {
    fun launchAgent(agent: Agent) {
        val sandbox = ProcessBuilder()
            .directory(isolatedDir)
            .environment(restrictedEnv)
            .command("k2script", "--sandbox", agent.script)
            .start()
        
        // Each agent is isolated
        // Communication only through explicit channels
    }
}
```

## Concentric Subnet Task Networks

```kotlin
// Concentric subnets: Nested communication layers
class ConcentricSubnetArchitecture {
    /*
     * Layer 0: Core subnet (most trusted)
     *   - BlackboardStore
     *   - FiduciaryCore
     *   
     * Layer 1: Processing subnet  
     *   - OCRExpert
     *   - TranscriptRunner
     *   - PatrickProcessor
     *   
     * Layer 2: External subnet
     *   - DivineIndexFetcher
     *   - IPFSGateway
     *   - CouchDBSync
     *   
     * Layer 3: Untrusted subnet
     *   - UserUploads
     *   - WebFetchers
     */
    
    val topology = ConcentricTopology(
        core = Subnet(trust = FULL, agents = listOf(BlackboardStore)),
        rings = listOf(
            Ring(trust = HIGH, agents = processingAgents),
            Ring(trust = MEDIUM, agents = externalAgents),
            Ring(trust = LOW, agents = untrustedAgents)
        )
    )
}
```

## The Problem

```kotlin
// K2Script gives us flat isolation
k2script --sandbox agent1.kts  // Isolated process
k2script --sandbox agent2.kts  // Another isolated process
k2script --sandbox agent3.kts  // Yet another isolated process

// But we need concentric communication
Ring0 -> Ring1  // Allowed
Ring1 -> Ring2  // Allowed  
Ring2 -> Ring1  // Restricted
Ring2 -> Ring0  // Forbidden
Ring1 <-> Ring1 // Peer communication within ring
```

## Bridging the Gap

### Option 1: Subnet-Aware K2Script
```kotlin
// Enhanced k2script with subnet awareness
@file:Subnet("processing")  // Declare subnet membership
@file:TrustLevel(HIGH)
@file:AllowedRings(0, 1, 2)  // Can talk to these rings

import fiduciary.concentric.*

// Agent automatically placed in correct subnet
class SubnetAwareAgent : Agent() {
    override suspend fun execute() {
        // Can only access allowed resources
        val blackboard = ring0.getBlackboard()  // Allowed
        val external = ring2.fetchExternal()    // Allowed
        val peer = ring1.getPeer()              // Allowed
        // val untrusted = ring3.access()       // Compile error!
    }
}
```

### Option 2: Nexus Subnet Orchestrator
```kotlin
// Nexus manages the concentric topology
class NexusSubnetOrchestrator {
    fun deployConcentricNetwork() {
        // Create subnet isolation
        val core = createCoreSubnet()
        val rings = createConcentricRings()
        
        // Deploy agents to appropriate subnets
        deployToSubnet(BlackboardStore(), core)
        deployToSubnet(OCRExpert(), rings[1])
        deployToSubnet(DivineIndexFetcher(), rings[2])
        
        // Configure routing rules
        configureInterRingRouting()
    }
    
    private fun configureInterRingRouting() {
        // Outward communication always allowed
        route.allow(from = 0, to = 1)
        route.allow(from = 1, to = 2)
        route.allow(from = 2, to = 3)
        
        // Inward communication restricted
        route.restrict(from = 2, to = 1, filter = SecurityFilter)
        route.deny(from = 3, to = 0)  // Never allow
    }
}
```

### Option 3: Virtual Subnet Overlays
```kotlin
// Use virtual networking over k2script sandboxes
class VirtualSubnetManager {
    fun createVirtualTopology() {
        // Each k2script sandbox gets virtual interfaces
        val sandboxes = agents.map { agent ->
            K2ScriptSandbox(
                agent = agent,
                networkConfig = VirtualNetworkConfig(
                    subnet = agent.requiredSubnet,
                    veth = createVirtualEthernet(),
                    routes = calculateRoutes(agent.subnet)
                )
            )
        }
        
        // Overlay concentric topology on flat sandboxes
        val overlay = ConcentricOverlay(sandboxes)
        overlay.enforceTopology()
    }
}
```

## Practical Architecture

```kotlin
// Hybrid approach: K2Script for isolation, Nexus for topology
class FiduciaryNetworkDeployment {
    fun deploy() {
        // 1. K2Script provides process isolation
        val isolatedAgents = agents.map { agent ->
            k2script.sandbox(agent)
        }
        
        // 2. Nexus provides logical subnet topology
        val topology = nexus.createConcentricTopology(
            isolatedAgents,
            subnetRules
        )
        
        // 3. Channel API provides controlled communication
        val channels = ChannelRouter(topology)
        channels.enforceConcentricRules()
        
        // 4. Result: Process isolation + logical subnets
        return FiduciaryNetwork(
            sandboxes = isolatedAgents,
            topology = topology,
            communication = channels
        )
    }
}
```

## Key Differences

| Aspect | K2Script Sandboxing | Concentric Subnets |
|--------|-------------------|-------------------|
| Isolation | Process-level | Trust-level |
| Communication | Explicit channels | Ring-based rules |
| Topology | Flat | Hierarchical |
| Trust | Binary (in/out) | Graduated (rings) |
| Routing | Point-to-point | Layer-based |

## Recommendations

1. **Use K2Script for process isolation** - It's good at this
2. **Use Nexus for subnet orchestration** - Manages the logical topology
3. **Use Channel API for communication** - Enforces ring-based rules
4. **Use virtual networking for true isolation** - When security critical

The key insight: K2Script sandboxes are building blocks, but the concentric subnet topology is a higher-level abstraction that Nexus needs to manage.