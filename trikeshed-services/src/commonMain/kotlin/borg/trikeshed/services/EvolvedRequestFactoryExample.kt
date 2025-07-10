@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.services

import borg.trikeshed.lib.*
import borg.trikeshed.reactor.http.HttpServerContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Comprehensive Example: Evolved RequestFactory with GWT and Wave Integration
 * 
 * Demonstrates:
 * - GWT-style type-safe service proxies
 * - Wave real-time collaboration
 * - CRDT conflict resolution
 * - Request batching
 * - Entity versioning
 */
class EvolvedRequestFactoryExample {
    
    internal lateinit var evolvedRequestFactory: EvolvedRequestFactory
    internal lateinit var gwtProxies: GWTServiceProxies
    internal lateinit var waveIntegration: WaveProtocolIntegration
    internal lateinit var mockContext: HttpServerContext

    /**
     * Initialize the evolved RequestFactory system
     */
    suspend fun initialize() {
        // Create mock HTTP context
        mockContext = createMockHttpContext()
        
        // Initialize evolved RequestFactory
        evolvedRequestFactory = EvolvedRequestFactory(mockContext)
        
        // Initialize GWT service proxies
        gwtProxies = GWTServiceProxies(evolvedRequestFactory)
        
        // Initialize Wave protocol integration
        waveIntegration = WaveProtocolIntegration()
        
        // Register services
        registerServices()
        
        println("✅ Evolved RequestFactory initialized with GWT and Wave integration")
    }

    /**
     * Example 1: GWT-style Service Proxy Usage
     */
    suspend fun demonstrateGWTServiceProxies() {
        println("\n🔧 Example 1: GWT-style Service Proxies")
        println("========================================")
        
        // Create service proxy
        val userServiceProxy = gwtProxies.createServiceProxy<UserService>()
        
        // Create request context for batching
        val requestContext = gwtProxies.createRequestContext()
        
        // Batch multiple operations
        val userCreation = userServiceProxy.create(
            entityClass = User::class,
            initialData = mapOf(
                "name" to "John Doe",
                "email" to "john@example.com",
                "age" to 30
            )
        )
        
        val userInvocation = userServiceProxy.invoke(
            methodName = "validateUser",
            "john@example.com",
            "password123"
        )
        
        // Add operations to batch
        requestContext.addCreation(userCreation)
        requestContext.addInvocation(userInvocation)
        
        // Fire batch request
        val batchResult = requestContext.fire()
        
        println("📦 Batch executed with ${batchResult.operations.size} operations")
        println("📊 Response: ${batchResult.response}")
    }

    /**
     * Example 2: Entity Proxy with Versioning
     */
    suspend fun demonstrateEntityVersioning() {
        println("\n📝 Example 2: Entity Proxy with Versioning")
        println("===========================================")
        
        // Create entity proxy
        val userProxy = gwtProxies.createEntityProxy<User>("user-123")
        
        // Simulate initial data
        val initialUser = User("user-123", "John Doe", "john@example.com", 30)
        userProxy.applyChanges(initialUser, EntityVersion("user-123", 1L))
        
        println("👤 Initial user: ${userProxy.getData()}")
        println("📋 Version: ${userProxy.getVersion()}")
        
        // Make changes
        userProxy.setProperty("name", "John Smith")
        userProxy.setProperty("age", 31)
        
        println("✏️  Modified: ${userProxy.isModified()}")
        println("🔄 Changes: ${userProxy.getProperty("name")}, ${userProxy.getProperty("age")}")
        
        // Save changes
        val update = userProxy.save()
        println("💾 Update result: ${update.isModified}")
    }

    /**
     * Example 3: Wave Real-time Collaboration
     */
    suspend fun demonstrateWaveCollaboration() {
        println("\n🌊 Example 3: Wave Real-time Collaboration")
        println("===========================================")
        
        // Create collaboration session
        val sessionId = "doc-session-123"
        val documentId = "doc-456"
        val session = waveIntegration.createSession(sessionId, documentId)
        
        // Join participants
        val alice = waveIntegration.joinSession(sessionId, "alice")
        val bob = waveIntegration.joinSession(sessionId, "bob")
        val charlie = waveIntegration.joinSession(sessionId, "charlie")
        
        println("👥 Participants joined: alice, bob, charlie")
        
        // Alice inserts text
        val aliceInsert = waveIntegration.createDocumentOperation(
            type = DocumentOperationType.INSERT,
            position = 0,
            content = "Hello, world!"
        )
        
        val aliceResult = waveIntegration.applyOperation(sessionId, "alice", aliceInsert)
        println("✍️  Alice inserted: 'Hello, world!'")
        
        // Bob inserts text at position 6
        val bobInsert = waveIntegration.createDocumentOperation(
            type = DocumentOperationType.INSERT,
            position = 6,
            content = "beautiful "
        )
        
        val bobResult = waveIntegration.applyOperation(sessionId, "bob", bobInsert)
        println("✍️  Bob inserted: 'beautiful ' at position 6")
        
        // Charlie deletes text
        val charlieDelete = waveIntegration.createDocumentOperation(
            type = DocumentOperationType.DELETE,
            position = 0,
            content = "Hello, "
        )
        
        val charlieResult = waveIntegration.applyOperation(sessionId, "charlie", charlieDelete)
        println("🗑️  Charlie deleted: 'Hello, '")
        
        // Get final document state
        val finalState = waveIntegration.getDocumentState(sessionId)
        println("📄 Final document: '${finalState?.content}'")
        
        // Subscribe to real-time updates
        waveIntegration.subscribeToUpdates(sessionId)
            .take(5)
            .collect { update ->
                when (update) {
                    is WaveUpdate.DocumentChanged -> {
                        println("📡 Real-time update: ${update.operation.type} at position ${update.operation.position}")
                    }
                    is WaveUpdate.ParticipantJoined -> {
                        println("👋 Participant joined: ${update.participantId}")
                    }
                    is WaveUpdate.ParticipantLeft -> {
                        println("👋 Participant left: ${update.participantId}")
                    }
                    else -> {}
                }
            }
    }

    /**
     * Example 4: CRDT Conflict Resolution
     */
    suspend fun demonstrateCRDTConflictResolution() {
        println("\n🔄 Example 4: CRDT Conflict Resolution")
        println("======================================")
        
        // Create CRDT entities
        val userCRDT = evolvedRequestFactory.createCRDTEntity("User", mapOf(
            "name" to "John Doe",
            "email" to "john@example.com"
        ))
        
        val documentCRDT = evolvedRequestFactory.createCRDTEntity("Document", mapOf(
            "title" to "Collaborative Document",
            "content" to "Initial content"
        ))
        
        println("📦 Created CRDT entities: ${userCRDT.id}, ${documentCRDT.id}")
        
        // Simulate concurrent updates
        val concurrentUpdate1 = EvolvedRequest.Update(
            serviceToken = "UserService",
            entityToken = "User",
            delta = EntityDelta(userCRDT.id, mapOf("name" to "John Smith")),
            version = EntityVersion(userCRDT.id, 1L)
        )
        
        val concurrentUpdate2 = EvolvedRequest.Update(
            serviceToken = "UserService", 
            entityToken = "User",
            delta = EntityDelta(userCRDT.id, mapOf("email" to "john.smith@example.com")),
            version = EntityVersion(userCRDT.id, 1L)
        )
        
        // Process concurrent updates
        val result1 = evolvedRequestFactory.process(serializeRequest(concurrentUpdate1))
        val result2 = evolvedRequestFactory.process(serializeRequest(concurrentUpdate2))
        
        println("⚡ Concurrent updates processed")
        println("📊 Result 1: ${result1.play.toList().toByteArray().decodeToString()}")
        println("📊 Result 2: ${result2.play.toList().toByteArray().decodeToString()}")
    }

    /**
     * Example 5: Performance Assessment
     */
    suspend fun demonstratePerformanceAssessment() {
        println("\n⚡ Example 5: Performance Assessment")
        println("===================================")
        
        val iterations = 1000
        val startTime = System.currentTimeMillis()
        
        // Benchmark service invocations
        repeat(iterations) { i ->
            val request = EvolvedRequest.Invoke(
                serviceToken = "UserService",
                methodToken = "getUser",
                args = listOf("user-$i").toIndexed()
            )
            
            evolvedRequestFactory.process(serializeRequest(request))
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val throughput = iterations.toDouble() / (duration / 1000.0)
        
        println("📊 Performance Results:")
        println("   Iterations: $iterations")
        println("   Duration: ${duration}ms")
        println("   Throughput: ${String.format("%.2f", throughput)} requests/second")
        println("   Average latency: ${String.format("%.2f", duration.toDouble() / iterations)}ms")
    }

    /**
     * Example 6: Integration with TrikeShed Patterns
     */
    suspend fun demonstrateTrikeShedIntegration() {
        println("\n🔗 Example 6: TrikeShed Patterns Integration")
        println("============================================")
        
        // Use Indexed<T> for data
        val userData = listOf("John", "Doe", "john@example.com", 30).toIndexed()
        println("📋 Indexed data: ${userData.play.toList()}")
        
        // Use Join<A,B> for relationships
        val userDocumentJoin = Join(
            left = User("user-123", "John Doe", "john@example.com", 30),
            right = Document("doc-456", "Collaborative Document", "Content here")
        )
        
        println("🔗 User-Document join: ${userDocumentJoin.left.name} -> ${userDocumentJoin.right.title}")
        
        // Create collaboration session with TrikeShed patterns
        val session = evolvedRequestFactory.createCollaborationSession("trikeshed-session")
        println("🌊 TrikeShed collaboration session created: ${session.sessionId}")
    }

    // Helper methods

    internal fun registerServices() {
        // Register service locators
        evolvedRequestFactory.registerServiceLocator("UserService") {
            UserService()
        }
        
        evolvedRequestFactory.registerServiceLocator("DocumentService") {
            DocumentService()
        }
        
        // Register method validators
        evolvedRequestFactory.registerMethodValidator("validateUser") { args ->
            args is Indexed<*> && args.play.toList().size >= 2
        }
        
        evolvedRequestFactory.registerMethodValidator("createUser") { args ->
            args is Indexed<*> && args.play.toList().isNotEmpty()
        }
    }

    internal fun createMockHttpContext(): HttpServerContext {
        // Simplified mock implementation
        return object : HttpServerContext {
            override val requestId: String = "mock-request-123"
            override val remoteAddress: String = "127.0.0.1"
            override val userAgent: String = "EvolvedRequestFactory/1.0"
        }
    }

    internal fun serializeRequest(request: EvolvedRequest): Indexed<Byte> {
        val json = when (request) {
            is EvolvedRequest.Invoke -> buildJsonObject {
                put("type", "invoke")
                put("serviceToken", request.serviceToken)
                put("methodToken", request.methodToken)
                putJsonArray("args") {
                    request.args.play.toList().forEach { arg ->
                        add(arg.toString())
                    }
                }
            }
            is EvolvedRequest.Update -> buildJsonObject {
                put("type", "update")
                put("serviceToken", request.serviceToken)
                put("entityToken", request.entityToken)
                putJsonObject("delta") {
                    put("entityId", request.delta.entityId)
                    putJsonObject("changes") {
                        request.delta.changes.forEach { (key, value) ->
                            put(key, value.toString())
                        }
                    }
                }
                putJsonObject("version") {
                    put("entityId", request.version.entityId)
                    put("version", request.version.version)
                }
            }
            else -> buildJsonObject { put("type", "unknown") }
        }
        
        return Json.encodeToString(json).encodeToByteArray().toIndexed()
    }
}

// Example service implementations

class UserService {
    fun validateUser(email: String, password: String): Boolean {
        return email.isNotEmpty() && password.isNotEmpty()
    }
    
    fun createUser(name: String, email: String, age: Int): User {
        return User(generateUserId(), name, email, age)
    }
    
    internal fun generateUserId(): String = "user-${System.currentTimeMillis()}"
}

class DocumentService {
    fun createDocument(title: String, content: String): Document {
        return Document(generateDocumentId(), title, content)
    }
    
    internal fun generateDocumentId(): String = "doc-${System.currentTimeMillis()}"
}

// Example data classes

data class User(
    val id: String,
    val name: String,
    val email: String,
    val age: Int
)

data class Document(
    val id: String,
    val title: String,
    val content: String
)

// Mock HTTP context implementation
interface HttpServerContext {
    val requestId: String
    val remoteAddress: String
    val userAgent: String
} 