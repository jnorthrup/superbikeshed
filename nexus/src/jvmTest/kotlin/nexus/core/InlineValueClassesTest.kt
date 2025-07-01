package nexus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class InlineValueClassesTest {
    
    @Test
    fun `NodeId validation works correctly`() {
        // Valid NodeId
        val validNodeId = NodeId("node-123")
        assertEquals("node-123", validNodeId.value)
        assertEquals("node-123", validNodeId.toString())
        
        // Invalid NodeId - should throw
        assertFailsWith<IllegalArgumentException> {
            NodeId("") // blank
        }
        
        assertFailsWith<IllegalArgumentException> {
            NodeId("node@123") // invalid characters
        }
    }
    
    @Test
    fun `NetworkId validation works correctly`() {
        // Valid NetworkId
        val validNetworkId = NetworkId("mainnet")
        assertEquals("mainnet", validNetworkId.value)
        
        // Invalid NetworkId - should throw
        assertFailsWith<IllegalArgumentException> {
            NetworkId("net@work") // invalid characters
        }
    }
    
    @Test
    fun `Port validation works correctly`() {
        // Valid ports
        val port1 = Port(8080)
        assertEquals(8080, port1.value)
        
        val port2 = Port(1)
        assertEquals(1, port2.value)
        
        val port3 = Port(65535)
        assertEquals(65535, port3.value)
        
        // Invalid ports - should throw
        assertFailsWith<IllegalArgumentException> {
            Port(0) // too low
        }
        
        assertFailsWith<IllegalArgumentException> {
            Port(65536) // too high
        }
    }
    
    @Test
    fun `TimeoutMs validation works correctly`() {
        // Valid timeouts
        val timeout1 = TimeoutMs(0)
        assertEquals(0L, timeout1.value)
        assertEquals("0ms", timeout1.toString())
        
        val timeout2 = TimeoutMs(30000)
        assertEquals(30000L, timeout2.value)
        assertEquals("30000ms", timeout2.toString())
        
        // Invalid timeout - should throw
        assertFailsWith<IllegalArgumentException> {
            TimeoutMs(-1) // negative
        }
    }
    
    @Test
    fun `MaxMessageSize validation and formatting works correctly`() {
        // Valid sizes
        val size1 = MaxMessageSize(1024)
        assertEquals(1024, size1.value)
        assertEquals("1KB", size1.toString())
        
        val size2 = MaxMessageSize(1024 * 1024)
        assertEquals(1024 * 1024, size2.value)
        assertEquals("1MB", size2.toString())
        
        val size3 = MaxMessageSize(512)
        assertEquals(512, size3.value)
        assertEquals("512B", size3.toString())
        
        // Invalid sizes - should throw
        assertFailsWith<IllegalArgumentException> {
            MaxMessageSize(0) // zero
        }
        
        assertFailsWith<IllegalArgumentException> {
            MaxMessageSize(101 * 1024 * 1024) // too large
        }
    }
    
    @Test
    fun `AuthToken hides value in toString`() {
        val token = AuthToken("super-secret-token-12345")
        assertEquals("super-secret-token-12345", token.value)
        assertEquals("*************************", token.toString()) // hidden
    }
    
    @Test
    fun `PluginName validation works correctly`() {
        // Valid plugin names
        val plugin1 = PluginName("myPlugin")
        assertEquals("myPlugin", plugin1.value)
        
        val plugin2 = PluginName("plugin_123")
        assertEquals("plugin_123", plugin2.value)
        
        // Invalid plugin names - should throw
        assertFailsWith<IllegalArgumentException> {
            PluginName("123plugin") // starts with number
        }
        
        assertFailsWith<IllegalArgumentException> {
            PluginName("") // blank
        }
    }
    
    @Test
    fun `ValueFactories provide convenient creation`() {
        val nodeId = ValueFactories.nodeId("test-node")
        assertEquals("test-node", nodeId.value)
        
        val networkId = ValueFactories.networkId("test-network")
        assertEquals("test-network", networkId.value)
        
        val port = ValueFactories.port(8080)
        assertEquals(8080, port.value)
        
        val timeout = ValueFactories.timeoutMs(5000)
        assertEquals(5000L, timeout.value)
    }
    
    @Test
    fun `DefaultNexusAgent uses inline value classes correctly`() {
        val agent = DefaultNexusAgent(
            ipfsPubSubService = TestIpfsPubSubService(),
            nodeId = NodeId("test-node"),
            networkId = NetworkId("test-network"),
            heartbeatIntervalMs = TimeoutMs(30000),
            maxMessageSize = MaxMessageSize(1024 * 1024),
            taskTimeoutMs = TimeoutMs(60000),
            authToken = AuthToken("test-token-1234567890123456"),
            allowedPeers = listOf(NodeId("peer1"), NodeId("peer2")),
            metricsIntervalMs = TimeoutMs(5000),
            gossipTopics = listOf(GossipTopic("test/topic1"), GossipTopic("test/topic2"))
        )
        
        assertEquals("test-node", agent.nodeId.value)
        assertEquals("test-network", agent.networkId.value)
        assertEquals(30000L, agent.heartbeatIntervalMs.value)
        assertEquals(1024 * 1024, agent.maxMessageSize.value)
        assertEquals("test-token-1234567890123456", agent.authToken?.value)
        assertEquals(2, agent.allowedPeers.size)
        assertEquals("peer1", agent.allowedPeers[0].value)
        assertEquals("peer2", agent.allowedPeers[1].value)
        assertEquals(2, agent.gossipTopics.size)
        assertEquals("test/topic1", agent.gossipTopics[0].value)
        assertEquals("test/topic2", agent.gossipTopics[1].value)
    }
}

// Simple test implementation for IpfsPubSubService
class TestIpfsPubSubService : IpfsPubSubService {
    override suspend fun publish(topic: String, message: String) {
        // Test implementation
    }
    
    override fun subscribe(topic: String): Flow<GossipMessage> {
        return flowOf()
    }
    
    override suspend fun unsubscribe(topic: String) {
        // Test implementation
    }
} 