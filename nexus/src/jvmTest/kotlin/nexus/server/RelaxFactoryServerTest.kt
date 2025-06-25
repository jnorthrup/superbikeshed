package nexus.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import nexus.api.*
import nexus.bridge.IpfsBridge
import nexus.core.*
import kotlin.test.*

/**
 * Comprehensive test suite for the RelaxFactory server.
 * 
 * Tests all components:
 * - QUIC server functionality
 * - CouchDB API endpoints
 * - IPFS bridge operations
 * - End-to-end request handling
 * - Configuration via DSL
 */
class RelaxFactoryServerTest {

    @Test
    fun `should create and configure RelaxFactory server using DSL`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        // Configure agent using DSL
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-server-001")
            networkId("test-network")
            heartbeatIntervalMs(5000)
            logLevel(LogLevel.DEBUG)
            capability(AgentCapability.GOSSIP)
            capability(AgentCapability.TELEMETRY)
        }
        
        // Configure server using DSL
        val serverConfig = relaxFactoryConfig {
            quicConfig {
                host("127.0.0.1")
                port(8081)
                maxConnections(100)
                enableTls(false)
            }
            enableHealthMonitoring(true)
            enableMetrics(true)
            maxRequestSize(1024 * 1024)
        }
        
        val server = RelaxFactoryServer(
            agent = agent,
            scope = CoroutineScope(Dispatchers.Default),
            config = serverConfig
        )
        
        assertFalse(server.isRunning())
        assertEquals("test-server-001", agent.nodeId)
        assertEquals("test-network", agent.networkId)
        assertEquals(8081, serverConfig.quicConfig.port)
        assertEquals("127.0.0.1", serverConfig.quicConfig.host)
        assertTrue(serverConfig.enableHealthMonitoring)
    }

    @Test
    fun `should start and stop RelaxFactory server`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-server-002")
            networkId("test-network")
            logLevel(LogLevel.INFO)
        }
        
        val serverConfig = relaxFactoryConfig {
            quicConfig {
                host("127.0.0.1")
                port(8082)
                enableTls(false)
            }
        }
        
        val server = RelaxFactoryServer(
            agent = agent,
            scope = CoroutineScope(Dispatchers.Default),
            config = serverConfig
        )
        
        // Test start
        server.start()
        assertTrue(server.isRunning())
        
        // Test stop
        server.stop()
        assertFalse(server.isRunning())
    }

    @Test
    fun `should handle CouchDB API operations through IPFS bridge`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-api-001")
            networkId("test-network")
            logLevel(LogLevel.DEBUG)
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test database creation
        ipfsBridge.createDatabase("testdb")
        
        // Test document storage
        val document = CouchDbDocument(
            _id = "testdoc",
            _rev = "1-abc123",
            data = mapOf("name" to "Test Document", "value" to 42)
        )
        
        val putResult = ipfsBridge.putDocument("testdb", "testdoc", document)
        assertEquals("testdoc", putResult.id)
        assertTrue(putResult.rev.isNotEmpty())
        
        // Test document retrieval
        val retrievedDoc = ipfsBridge.getDocument("testdb", "testdoc")
        assertEquals("testdoc", retrievedDoc._id)
        assertEquals(putResult.rev, retrievedDoc._rev)
        assertFalse(retrievedDoc._deleted)
        
        // Test document deletion
        val deleteResult = ipfsBridge.deleteDocument("testdb", "testdoc", putResult.rev)
        assertEquals("testdoc", deleteResult.id)
        assertTrue(deleteResult.rev.isNotEmpty())
        
        // Test that deleted document throws exception
        assertFailsWith<DocumentNotFoundException> {
            ipfsBridge.getDocument("testdb", "testdoc")
        }
    }

    @Test
    fun `should handle bulk document operations`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-bulk-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("bulkdb")
        
        val documents = listOf(
            CouchDbDocument(_id = "doc1", _rev = "1-abc", data = mapOf("name" to "Doc 1")),
            CouchDbDocument(_id = "doc2", _rev = "1-def", data = mapOf("name" to "Doc 2")),
            CouchDbDocument(_id = "doc3", _rev = "1-ghi", data = mapOf("name" to "Doc 3"))
        )
        
        val bulkRequest = CouchDbBulkRequest(docs = documents)
        val bulkResult = ipfsBridge.bulkDocuments("bulkdb", bulkRequest)
        
        assertEquals(3, bulkResult.results.size)
        assertTrue(bulkResult.results.all { it.ok })
        
        // Verify documents were stored
        documents.forEach { doc ->
            val retrieved = ipfsBridge.getDocument("bulkdb", doc._id)
            assertEquals(doc._id, retrieved._id)
        }
    }

    @Test
    fun `should handle database operations`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-db-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test database creation
        ipfsBridge.createDatabase("testdb")
        
        // Test database info
        val dbInfo = ipfsBridge.getDatabaseInfo("testdb")
        assertEquals("testdb", dbInfo.db_name)
        assertEquals(0, dbInfo.doc_count)
        assertEquals(0, dbInfo.doc_del_count)
        
        // Add a document
        val document = CouchDbDocument(_id = "testdoc", _rev = "1-abc", data = mapOf("test" to true))
        ipfsBridge.putDocument("testdb", "testdoc", document)
        
        // Verify document count increased
        val updatedDbInfo = ipfsBridge.getDatabaseInfo("testdb")
        assertEquals(1, updatedDbInfo.doc_count)
        
        // Test database deletion
        ipfsBridge.deleteDatabase("testdb")
        
        // Verify database no longer exists
        assertFailsWith<DatabaseNotFoundException> {
            ipfsBridge.getDatabaseInfo("testdb")
        }
    }

    @Test
    fun `should handle changes feed`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-changes-001")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        ipfsBridge.createDatabase("changesdb")
        
        // Add documents
        val doc1 = CouchDbDocument(_id = "doc1", _rev = "1-abc", data = mapOf("name" to "Doc 1"))
        val doc2 = CouchDbDocument(_id = "doc2", _rev = "1-def", data = mapOf("name" to "Doc 2"))
        
        ipfsBridge.putDocument("changesdb", "doc1", doc1)
        ipfsBridge.putDocument("changesdb", "doc2", doc2)
        
        // Get changes feed
        val changes = ipfsBridge.getChanges("changesdb", emptyMap())
        
        assertTrue(changes.results.size >= 2)
        assertTrue(changes.results.any { it.id == "doc1" })
        assertTrue(changes.results.any { it.id == "doc2" })
        assertFalse(changes.results.any { it.deleted })
        
        // Delete a document
        val putResult = ipfsBridge.putDocument("changesdb", "doc1", doc1)
        ipfsBridge.deleteDocument("changesdb", "doc1", putResult.rev)
        
        // Verify deletion appears in changes
        val updatedChanges = ipfsBridge.getChanges("changesdb", emptyMap())
        assertTrue(updatedChanges.results.any { it.id == "doc1" && it.deleted })
    }

    @Test
    fun `should handle network synchronization via PubSub`() = runTest {
        val testIpfsService1 = TestIpfsPubSubService()
        val testIpfsService2 = TestIpfsPubSubService()
        
        val agent1 = defaultNexusAgent {
            ipfsPubSubService(testIpfsService1)
            nodeId("test-sync-001")
            networkId("test-network")
        }
        
        val agent2 = defaultNexusAgent {
            ipfsPubSubService(testIpfsService2)
            nodeId("test-sync-002")
            networkId("test-network")
        }
        
        val bridge1 = IpfsBridge(agent1, testIpfsService1)
        val bridge2 = IpfsBridge(agent2, testIpfsService2)
        
        // Create database on first node
        bridge1.createDatabase("syncdb")
        
        // Add document on first node
        val document = CouchDbDocument(_id = "syncdoc", _rev = "1-abc", data = mapOf("synced" to true))
        bridge1.putDocument("syncdb", "syncdoc", document)
        
        // Simulate network propagation (in real implementation, this would happen via IPFS PubSub)
        // For testing, we'll manually trigger the index update
        val indexEntry = DocumentIndexEntry(
            id = "syncdoc",
            dbName = "syncdb",
            cid = "Qm1234567890abcdef",
            rev = "2-def",
            deleted = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        val message = GossipMessage(
            sender = "test-sync-001",
            topic = "nexus/document-index",
            timestamp = System.currentTimeMillis(),
            payload = """{"id":"syncdoc","dbName":"syncdb","cid":"Qm1234567890abcdef","rev":"2-def","deleted":false,"createdAt":${System.currentTimeMillis()},"updatedAt":${System.currentTimeMillis()}}"""
        )
        
        // Simulate receiving the message on second node
        bridge2.handleIndexUpdate(message)
        
        // Verify document is available on second node
        val retrievedDoc = bridge2.getDocument("syncdb", "syncdoc")
        assertEquals("syncdoc", retrievedDoc._id)
    }

    @Test
    fun `should handle QUIC server configuration`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-quic-001")
            networkId("test-network")
        }
        
        val quicConfig = quicServerConfig {
            host("127.0.0.1")
            port(8083)
            maxConnections(500)
            connectionTimeoutMs(15000)
            maxStreamsPerConnection(50)
            enableTls(false)
            alpnProtocols("h3", "h3-29")
            enableRetry(true)
            maxRetries(3)
            keepAliveMs(20000)
            maxIdleTimeoutMs(45000)
        }
        
        val quicServer = QuicServer(agent, CoroutineScope(Dispatchers.Default), quicConfig)
        
        assertEquals("127.0.0.1", quicConfig.host)
        assertEquals(8083, quicConfig.port)
        assertEquals(500, quicConfig.maxConnections)
        assertEquals(15000L, quicConfig.connectionTimeoutMs)
        assertEquals(50, quicConfig.maxStreamsPerConnection)
        assertFalse(quicConfig.enableTls)
        assertEquals(2, quicConfig.alpnProtocols.size)
        assertTrue(quicConfig.enableRetry)
        assertEquals(3, quicConfig.maxRetries)
        assertEquals(20000L, quicConfig.keepAliveMs)
        assertEquals(45000L, quicConfig.maxIdleTimeoutMs)
        
        assertFalse(quicServer.isRunning())
    }

    @Test
    fun `should handle CouchDB API routing`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-api-002")
            networkId("test-network")
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        val couchDbApi = CouchDbApi(agent, ipfsBridge)
        
        // Create a mock stream for testing
        val mockStream = QuicStream(
            id = "test-stream-001",
            connectionId = "test-connection-001",
            direction = StreamDirection.INBOUND,
            headers = mapOf("Content-Type" to "application/json"),
            data = kotlinx.coroutines.flow.flowOf("GET /testdb/testdoc HTTP/3".toByteArray())
        )
        
        // Test API request handling
        val response = couchDbApi.handleRequest(mockStream)
        
        // Should return 404 since database doesn't exist
        assertEquals(404, response.status)
        assertTrue(response.body.contains("Not Found"))
    }

    @Test
    fun `should handle complex server configuration`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        // Complex agent configuration
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("complex-server-001")
            networkId("production-network")
            
            // Network settings
            heartbeatIntervalMs(30000)
            maxMessageSize(2 * 1024 * 1024)
            maxConcurrentTasks(50)
            taskTimeoutMs(120000)
            retryAttempts(5)
            
            // Capabilities
            capability(AgentCapability.GOSSIP)
            capability(AgentCapability.TELEMETRY)
            capability(AgentCapability.TASK_EXECUTION)
            capability(AgentCapability.WORKFLOW_MANAGEMENT)
            capability(AgentCapability.PEER_DISCOVERY)
            capability(AgentCapability.DATA_SYNC)
            capability(AgentCapability.AI_REASONING)
            capability(AgentCapability.PLUGIN_MANAGEMENT)
            
            // Security
            authToken("complex-token-xyz789")
            enableEncryption(true)
            allowedPeer("peer-001")
            allowedPeer("peer-002")
            allowedPeer("peer-003")
            
            // Monitoring
            logLevel(LogLevel.INFO)
            enableMetrics(true)
            metricsIntervalMs(15000)
            
            // Gossip topics
            gossipTopic("nexus/production/events")
            gossipTopic("nexus/production/alerts")
            gossipTopic("nexus/production/metrics")
            gossipTopic("nexus/production/health")
            
            // Custom configuration
            customConfig("environment", "production")
            customConfig("region", "us-west-2")
            customConfig("instance_type", "c5.2xlarge")
            customConfig("auto_scaling", "enabled")
        }
        
        // Complex server configuration
        val serverConfig = relaxFactoryConfig {
            quicConfig {
                host("0.0.0.0")
                port(8443)
                maxConnections(2000)
                connectionTimeoutMs(45000)
                maxStreamsPerConnection(200)
                enableTls(true)
                certificatePath("/etc/ssl/certs/server.crt")
                privateKeyPath("/etc/ssl/private/server.key")
                alpnProtocols("h3", "h3-29", "h3-28")
                enableRetry(true)
                maxRetries(5)
                keepAliveMs(60000)
                maxIdleTimeoutMs(120000)
            }
            enableHealthMonitoring(true)
            enableMetrics(true)
            enableLogging(true)
            maxRequestSize(50 * 1024 * 1024) // 50MB
            requestTimeoutMs(60000)
            enableCors(true)
            corsOrigins("https://app.example.com", "https://api.example.com")
            enableRateLimiting(true)
            rateLimitRequestsPerMinute(5000)
            enableCompression(true)
            compressionLevel(9)
            enableTls(true)
            certificatePath("/etc/ssl/certs/server.crt")
            privateKeyPath("/etc/ssl/private/server.key")
            enableAuthentication(true)
            authToken("server-auth-token-abc123")
            enableAuditLogging(true)
            auditLogPath("/var/log/relaxfactory/audit.log")
        }
        
        // Verify complex configuration
        assertEquals("complex-server-001", agent.nodeId)
        assertEquals("production-network", agent.networkId)
        assertEquals(8, agent.capabilities.size)
        assertEquals("complex-token-xyz789", agent.authToken)
        assertTrue(agent.enableEncryption)
        assertEquals(3, agent.allowedPeers.size)
        assertEquals(4, agent.gossipTopics.size)
        assertEquals(4, agent.customConfig.size)
        
        assertEquals(8443, serverConfig.quicConfig.port)
        assertEquals(2000, serverConfig.quicConfig.maxConnections)
        assertTrue(serverConfig.quicConfig.enableTls)
        assertEquals(3, serverConfig.quicConfig.alpnProtocols.size)
        assertEquals(50 * 1024 * 1024, serverConfig.maxRequestSize)
        assertEquals(60000L, serverConfig.requestTimeoutMs)
        assertTrue(serverConfig.enableRateLimiting)
        assertEquals(5000, serverConfig.rateLimitRequestsPerMinute)
        assertEquals(9, serverConfig.compressionLevel)
        assertTrue(serverConfig.enableAuthentication)
        assertTrue(serverConfig.enableAuditLogging)
    }

    @Test
    fun `should handle error conditions gracefully`() = runTest {
        val testIpfsService = TestIpfsPubSubService()
        
        val agent = defaultNexusAgent {
            ipfsPubSubService(testIpfsService)
            nodeId("test-error-001")
            networkId("test-network")
            logLevel(LogLevel.ERROR)
        }
        
        val ipfsBridge = IpfsBridge(agent, testIpfsService)
        
        // Test accessing non-existent database
        assertFailsWith<DatabaseNotFoundException> {
            ipfsBridge.getDatabaseInfo("nonexistent")
        }
        
        // Test accessing non-existent document
        assertFailsWith<DocumentNotFoundException> {
            ipfsBridge.getDocument("nonexistent", "nonexistent")
        }
        
        // Test creating duplicate database
        ipfsBridge.createDatabase("duplicatedb")
        assertFailsWith<DatabaseExistsException> {
            ipfsBridge.createDatabase("duplicatedb")
        }
        
        // Test deleting non-existent database
        assertFailsWith<DatabaseNotFoundException> {
            ipfsBridge.deleteDatabase("nonexistent")
        }
    }
} 