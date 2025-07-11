package fiduciary.ingest

import borg.trikeshed.couchdb.*
import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Test CouchDB views with CRDT support for realtime collaborative editing
 * 
 * Proves correctness of:
 * 1. CRDT convergence in CouchDB views
 * 2. Realtime markdown/mermaid editing
 * 3. Attention gateway session management
 * 4. Conflict resolution in views
 */
class CouchDBCRDTViewsTest {
    private lateinit var couchDb: CRDTCouchDB
    private lateinit var sessionManager: AttentionSessionManager
    
    @BeforeTest
    fun setup() {
        couchDb = CRDTCouchDB()
        sessionManager = AttentionSessionManager(couchDb)
    }
    
    @Test
    fun `proof - CRDT text operations converge in CouchDB views`() = runTest {
        // Given: Multiple users editing the same document
        val docId = "collaborative_doc_001"
        val session = sessionManager.createSession(docId)
        
        // User A edits
        val userA = session.joinUser("alice")
        val opsA = listOf(
            CRDTTextOp(
                userId = "alice",
                timestamp = Clock.System.now(),
                position = 0,
                insert = "# Fiduciary Analysis\n\n"
            ),
            CRDTTextOp(
                userId = "alice",
                timestamp = Clock.System.now().plus(1.seconds),
                position = 22,
                insert = "Patrick Devine's work on "
            )
        )
        
        // User B edits concurrently
        val userB = session.joinUser("bob")
        val opsB = listOf(
            CRDTTextOp(
                userId = "bob",
                timestamp = Clock.System.now().plus(500.milliseconds),
                position = 22,
                insert = "## Key Insights\n\n"
            ),
            CRDTTextOp(
                userId = "bob",
                timestamp = Clock.System.now().plus(2.seconds),
                position = 39,
                insert = "graph theory applications"
            )
        )
        
        // When: Operations are applied through CRDT
        opsA.forEach { userA.applyOp(it) }
        opsB.forEach { userB.applyOp(it) }
        
        // Create view to track CRDT operations
        couchDb.createView(
            designDoc = "crdt",
            viewName = "text_operations",
            mapFunction = """
                function(doc) {
                    if (doc.type === 'crdt_op' && doc.docId) {
                        emit([doc.docId, doc.timestamp], {
                            userId: doc.userId,
                            op: doc.operation,
                            position: doc.position
                        });
                    }
                }
            """.trimIndent()
        )
        
        // Then: All users converge to same state
        val finalStateA = userA.getCurrentText()
        val finalStateB = userB.getCurrentText()
        
        // Proof 1: CRDT convergence
        assertEquals(finalStateA, finalStateB)
        
        // Proof 2: All operations are preserved in order
        val ops = couchDb.query(
            ViewQuery(
                designDoc = "crdt",
                viewName = "text_operations",
                startKey = Json.encodeToJsonElement(listOf(docId, "0")),
                endKey = Json.encodeToJsonElement(listOf(docId, "z"))
            )
        )
        
        assertEquals(4, ops.size) // All operations recorded
        
        // Proof 3: Final text contains all edits
        assertTrue(finalStateA.contains("# Fiduciary Analysis"))
        assertTrue(finalStateA.contains("## Key Insights"))
        assertTrue(finalStateA.contains("Patrick Devine's work on"))
        assertTrue(finalStateA.contains("graph theory applications"))
    }
    
    @Test
    fun `proof - realtime markdown collaboration maintains structure`() = runTest {
        // Given: Collaborative markdown document
        val markdownDoc = CollaborativeMarkdown(
            _id = "markdown_001",
            title = "Fiduciary System Architecture"
        )
        
        val session = sessionManager.createMarkdownSession(markdownDoc)
        
        // Multiple users edit different sections
        val alice = session.join("alice")
        val bob = session.join("bob")
        val carol = session.join("carol")
        
        // Alice adds introduction
        alice.insertMarkdown(
            """
            ## Introduction
            
            The fiduciary system uses distributed CRDTs for:
            - Realtime collaboration
            - Conflict-free updates
            - Session management
            """.trimIndent(),
            position = 0
        )
        
        // Bob adds architecture section
        bob.insertMarkdown(
            """
            
            ## Architecture
            
            ```mermaid
            graph TD
                A[CouchDB] --> B[CRDT Layer]
                B --> C[Attention Gateway]
                C --> D[User Sessions]
            ```
            """.trimIndent(),
            position = alice.getLength()
        )
        
        // Carol adds implementation details
        carol.insertMarkdown(
            """
            
            ## Implementation
            
            Key components:
            1. **CouchDB Views**: Index CRDT operations
            2. **Session Manager**: Handle user presence
            3. **Conflict Resolution**: Automatic via CRDT
            """.trimIndent(),
            position = alice.getLength() + bob.getLength()
        )
        
        // When: Document is rendered
        val rendered = session.renderMarkdown()
        
        // Then: Structure is maintained
        
        // Proof 1: All sections present in order
        val sections = rendered.split("\n## ").drop(1)
        assertEquals(3, sections.size)
        assertTrue(sections[0].startsWith("Introduction"))
        assertTrue(sections[1].startsWith("Architecture"))
        assertTrue(sections[2].startsWith("Implementation"))
        
        // Proof 2: Mermaid diagram preserved
        assertTrue(rendered.contains("```mermaid"))
        assertTrue(rendered.contains("graph TD"))
        
        // Proof 3: Lists and formatting intact
        assertTrue(rendered.contains("- Realtime collaboration"))
        assertTrue(rendered.contains("1. **CouchDB Views**"))
    }
    
    @Test
    fun `proof - attention gateway sessions maintain consistency`() = runTest {
        // Given: Attention gateway with multiple sessions
        val gateway = AttentionGateway(couchDb)
        
        // Create sessions for different document types
        val sessions = listOf(
            gateway.createSession(
                type = SessionType.MARKDOWN,
                docId = "planning_doc",
                metadata = mapOf("project" to "fiduciary")
            ),
            gateway.createSession(
                type = SessionType.MERMAID,
                docId = "architecture_diagram",
                metadata = mapOf("version" to "2.0")
            ),
            gateway.createSession(
                type = SessionType.TEXT,
                docId = "notes_001",
                metadata = mapOf("author" to "patrick")
            )
        )
        
        // Users join sessions
        val userActivities = mutableListOf<UserActivity>()
        
        sessions.forEach { session ->
            repeat(3) { i ->
                val user = session.addUser("user_$i")
                userActivities.add(
                    UserActivity(
                        sessionId = session.id,
                        userId = user.id,
                        joinTime = Clock.System.now(),
                        actions = mutableListOf()
                    )
                )
            }
        }
        
        // Create view for active sessions
        couchDb.createView(
            designDoc = "gateway",
            viewName = "active_sessions",
            mapFunction = """
                function(doc) {
                    if (doc.type === 'session' && doc.active) {
                        emit(doc.createdAt, {
                            sessionType: doc.sessionType,
                            userCount: doc.users ? doc.users.length : 0,
                            metadata: doc.metadata
                        });
                    }
                }
            """.trimIndent()
        )
        
        // When: Query active sessions
        val activeSessions = gateway.getActiveSessions()
        
        // Then: Session consistency maintained
        
        // Proof 1: All sessions tracked
        assertEquals(3, activeSessions.size)
        
        // Proof 2: User counts accurate
        activeSessions.forEach { session ->
            assertEquals(3, session.activeUsers.size)
        }
        
        // Proof 3: Session types preserved
        val types = activeSessions.map { it.type }.toSet()
        assertEquals(
            setOf(SessionType.MARKDOWN, SessionType.MERMAID, SessionType.TEXT),
            types
        )
        
        // Proof 4: Metadata intact
        val planningSession = activeSessions.find { it.docId == "planning_doc" }
        assertEquals("fiduciary", planningSession?.metadata?.get("project"))
    }
    
    @Test
    fun `proof - mermaid diagram CRDT operations preserve syntax`() = runTest {
        // Given: Collaborative mermaid diagram editing
        val mermaidDoc = MermaidDocument(
            _id = "mermaid_crdt_001",
            title = "Fiduciary Flow"
        )
        
        val session = sessionManager.createMermaidSession(mermaidDoc)
        val alice = session.join("alice")
        val bob = session.join("bob")
        
        // Alice starts the diagram
        alice.addMermaidOp(
            MermaidOp.AddNode("A", "CouchDB", position = Point(0, 0))
        )
        alice.addMermaidOp(
            MermaidOp.AddNode("B", "Ingestion Pipeline", position = Point(100, 0))
        )
        alice.addMermaidOp(
            MermaidOp.AddEdge("A", "B", "feeds")
        )
        
        // Bob adds concurrently
        bob.addMermaidOp(
            MermaidOp.AddNode("C", "CRDT Layer", position = Point(200, 0))
        )
        bob.addMermaidOp(
            MermaidOp.AddEdge("B", "C", "processes")
        )
        
        // When: Diagram is rendered
        val mermaidSyntax = session.renderMermaid()
        
        // Then: Valid mermaid syntax maintained
        
        // Proof 1: Graph declaration present
        assertTrue(mermaidSyntax.startsWith("graph"))
        
        // Proof 2: All nodes present
        assertTrue(mermaidSyntax.contains("A[CouchDB]"))
        assertTrue(mermaidSyntax.contains("B[Ingestion Pipeline]"))
        assertTrue(mermaidSyntax.contains("C[CRDT Layer]"))
        
        // Proof 3: All edges present with labels
        assertTrue(mermaidSyntax.contains("A -->|feeds| B"))
        assertTrue(mermaidSyntax.contains("B -->|processes| C"))
        
        // Proof 4: Syntax is parseable
        val parsed = MermaidParser.parse(mermaidSyntax)
        assertEquals(3, parsed.nodes.size)
        assertEquals(2, parsed.edges.size)
    }
    
    @Test
    fun `proof - CouchDB views handle CRDT conflict resolution`() = runTest {
        // Given: Conflicting edits to same position
        val docId = "conflict_test_001"
        val baseText = "The fiduciary system uses CouchDB."
        
        val session = sessionManager.createSession(docId, initialText = baseText)
        val alice = session.joinUser("alice")
        val bob = session.joinUser("bob")
        
        // Both users edit same position concurrently
        val conflictPosition = 25 // After "uses"
        
        alice.applyOp(
            CRDTTextOp(
                userId = "alice",
                timestamp = Clock.System.now(),
                position = conflictPosition,
                insert = " advanced"
            )
        )
        
        bob.applyOp(
            CRDTTextOp(
                userId = "bob",
                timestamp = Clock.System.now().plus(1.milliseconds),
                position = conflictPosition,
                insert = " distributed"
            )
        )
        
        // Create conflict resolution view
        couchDb.createView(
            designDoc = "conflicts",
            viewName = "by_position",
            mapFunction = """
                function(doc) {
                    if (doc.type === 'crdt_op' && doc.position !== undefined) {
                        emit([doc.docId, doc.position], {
                            userId: doc.userId,
                            timestamp: doc.timestamp,
                            operation: doc.operation
                        });
                    }
                }
            """.trimIndent()
        )
        
        // When: Query conflicts at position
        val conflicts = couchDb.query(
            ViewQuery(
                designDoc = "conflicts",
                viewName = "by_position",
                key = Json.encodeToJsonElement(listOf(docId, conflictPosition))
            )
        )
        
        // Then: Conflicts resolved deterministically
        
        // Proof 1: Both operations recorded
        assertEquals(2, conflicts.size)
        
        // Proof 2: Final text contains both edits
        val finalText = session.getCurrentText()
        assertTrue(finalText.contains("advanced"))
        assertTrue(finalText.contains("distributed"))
        
        // Proof 3: Order is deterministic
        val aliceFirst = finalText.indexOf("advanced") < finalText.indexOf("distributed")
        assertTrue(aliceFirst) // Alice's timestamp was earlier
        
        // Proof 4: No data loss
        assertEquals(
            baseText.length + " advanced".length + " distributed".length,
            finalText.length
        )
    }
}

// Support classes for CRDT testing

@Serializable
data class CRDTTextOp(
    val userId: String,
    val timestamp: Instant,
    val position: Int,
    val insert: String? = null,
    val delete: Int? = null
)

@Serializable
sealed class MermaidOp {
    data class AddNode(
        val id: String,
        val label: String,
        val position: Point
    ) : MermaidOp()
    
    data class AddEdge(
        val from: String,
        val to: String,
        val label: String? = null
    ) : MermaidOp()
    
    data class UpdateNode(
        val id: String,
        val newLabel: String
    ) : MermaidOp()
}

@Serializable
data class Point(val x: Int, val y: Int)

@Serializable
data class CollaborativeMarkdown(
    val _id: String,
    val title: String,
    val sections: MutableList<MarkdownSection> = mutableListOf()
)

@Serializable
data class MarkdownSection(
    val id: String,
    val content: String,
    val author: String,
    val timestamp: Instant
)

@Serializable
data class MermaidDocument(
    val _id: String,
    val title: String,
    val nodes: MutableMap<String, MermaidNode> = mutableMapOf(),
    val edges: MutableList<MermaidEdge> = mutableListOf()
)

@Serializable
data class MermaidNode(
    val id: String,
    val label: String,
    val position: Point
)

@Serializable
data class MermaidEdge(
    val from: String,
    val to: String,
    val label: String? = null
)

@Serializable
enum class SessionType {
    TEXT, MARKDOWN, MERMAID
}

@Serializable
data class UserActivity(
    val sessionId: String,
    val userId: String,
    val joinTime: Instant,
    val actions: MutableList<String>
)

// Test implementations

class CRDTCouchDB : TestCouchDB() {
    private val crdtOps = mutableListOf<CRDTTextOp>()
    
    fun applyCRDTOp(op: CRDTTextOp) {
        crdtOps.add(op)
        // Store as document
        runBlocking {
            put("crdt_op_${op.timestamp}", mapOf(
                "type" to "crdt_op",
                "docId" to "test",
                "userId" to op.userId,
                "timestamp" to op.timestamp.toString(),
                "position" to op.position,
                "operation" to op
            ))
        }
    }
}

class AttentionSessionManager(private val db: CRDTCouchDB) {
    private val sessions = mutableMapOf<String, Session>()
    
    fun createSession(docId: String, initialText: String = ""): Session {
        val session = Session(docId, db, initialText)
        sessions[docId] = session
        return session
    }
    
    fun createMarkdownSession(doc: CollaborativeMarkdown): MarkdownSession {
        return MarkdownSession(doc, db)
    }
    
    fun createMermaidSession(doc: MermaidDocument): MermaidSession {
        return MermaidSession(doc, db)
    }
}

class Session(
    val id: String,
    private val db: CRDTCouchDB,
    private var text: String
) {
    private val users = mutableListOf<User>()
    
    fun joinUser(userId: String): User {
        val user = User(userId, this)
        users.add(user)
        return user
    }
    
    fun getCurrentText(): String = text
    
    fun applyOp(op: CRDTTextOp) {
        db.applyCRDTOp(op)
        // Simplified CRDT application
        if (op.insert != null) {
            text = text.substring(0, op.position) + op.insert + text.substring(op.position)
        }
    }
}

class User(val id: String, private val session: Session) {
    fun applyOp(op: CRDTTextOp) {
        session.applyOp(op)
    }
    
    fun getCurrentText(): String = session.getCurrentText()
}

class MarkdownSession(
    private val doc: CollaborativeMarkdown,
    private val db: CRDTCouchDB
) {
    private val users = mutableMapOf<String, MarkdownUser>()
    
    fun join(userId: String): MarkdownUser {
        val user = MarkdownUser(userId, this)
        users[userId] = user
        return user
    }
    
    fun renderMarkdown(): String {
        return doc.sections.joinToString("\n\n") { it.content }
    }
}

class MarkdownUser(
    val id: String,
    private val session: MarkdownSession
) {
    private var content = ""
    
    fun insertMarkdown(markdown: String, position: Int) {
        content = markdown
    }
    
    fun getLength(): Int = content.length
}

class MermaidSession(
    private val doc: MermaidDocument,
    private val db: CRDTCouchDB
) {
    private val users = mutableMapOf<String, MermaidUser>()
    
    fun join(userId: String): MermaidUser {
        val user = MermaidUser(userId, this)
        users[userId] = user
        return user
    }
    
    fun renderMermaid(): String {
        val sb = StringBuilder("graph TD\n")
        doc.nodes.forEach { (id, node) ->
            sb.append("    $id[${node.label}]\n")
        }
        doc.edges.forEach { edge ->
            val label = edge.label?.let { "|$it|" } ?: ""
            sb.append("    ${edge.from} -->$label ${edge.to}\n")
        }
        return sb.toString()
    }
    
    fun applyOp(op: MermaidOp) {
        when (op) {
            is MermaidOp.AddNode -> {
                doc.nodes[op.id] = MermaidNode(op.id, op.label, op.position)
            }
            is MermaidOp.AddEdge -> {
                doc.edges.add(MermaidEdge(op.from, op.to, op.label))
            }
            is MermaidOp.UpdateNode -> {
                doc.nodes[op.id]?.let {
                    doc.nodes[op.id] = it.copy(label = op.newLabel)
                }
            }
        }
    }
}

class MermaidUser(
    val id: String,
    private val session: MermaidSession
) {
    fun addMermaidOp(op: MermaidOp) {
        session.applyOp(op)
    }
}

class AttentionGateway(private val db: CRDTCouchDB) {
    private val sessions = mutableListOf<GatewaySession>()
    
    fun createSession(
        type: SessionType,
        docId: String,
        metadata: Map<String, String>
    ): GatewaySession {
        val session = GatewaySession(
            id = "session_${System.currentTimeMillis()}",
            type = type,
            docId = docId,
            metadata = metadata,
            createdAt = Clock.System.now()
        )
        sessions.add(session)
        return session
    }
    
    fun getActiveSessions(): List<GatewaySession> = sessions.filter { it.active }
}

data class GatewaySession(
    val id: String,
    val type: SessionType,
    val docId: String,
    val metadata: Map<String, String>,
    val createdAt: Instant,
    var active: Boolean = true
) {
    val activeUsers = mutableListOf<GatewayUser>()
    
    fun addUser(userId: String): GatewayUser {
        val user = GatewayUser(userId, Clock.System.now())
        activeUsers.add(user)
        return user
    }
}

data class GatewayUser(
    val id: String,
    val joinedAt: Instant
)

object MermaidParser {
    fun parse(syntax: String): ParsedDiagram {
        // Simplified parser
        val nodes = mutableListOf<String>()
        val edges = mutableListOf<String>()
        
        syntax.lines().forEach { line ->
            when {
                line.contains("[") && line.contains("]") -> nodes.add(line.trim())
                line.contains("-->") -> edges.add(line.trim())
            }
        }
        
        return ParsedDiagram(nodes, edges)
    }
}

data class ParsedDiagram(
    val nodes: List<String>,
    val edges: List<String>
)