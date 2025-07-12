package borg.trikeshed.wave

import borg.trikeshed.couchdb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Real-time Graph Demo with Live Collaboration
 * 
 * Demonstrates:
 * - Real-time mermaid/dot document editing
 * - Live graph visualization updates
 * - Multi-user collaboration with CRDT
 * - Git-style history and audit trail
 */
class RealtimeGraphDemo(
    private val couchDB: CouchDBClient
) {
    private val waveCRDT = CouchDBWaveCRDT(couchDB)
    
    /**
     * Demo: Collaborative Mermaid Diagram Editing
     */
    suspend fun demonstrateMermaidCollaboration() = coroutineScope {
        println("🚀 Starting Mermaid Collaboration Demo")
        
        // Create a collaborative mermaid document
        val documentId = "mermaid-demo-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val initialMermaid = """
            graph TD
                A[Start] --> B[Process]
                B --> C[End]
        """.trimIndent()
        
        val document = waveCRDT.createDocument(
            id = documentId,
            type = DocumentCRDT.DocumentType.MERMAID,
            initialContent = initialMermaid,
            metadata = DocumentMetadata(
                title = "Collaborative Mermaid Demo",
                description = "Real-time collaborative mermaid diagram editing",
                author = "demo-user"
            )
        )
        
        println("📄 Created document: ${document._id}")
        println("📊 Initial mermaid content:")
        println(document.content)
        
        // Simulate multiple users joining and editing
        val user1 = "alice"
        val user2 = "bob"
        val user3 = "charlie"
        
        // User 1 joins and adds a node
        launch {
            val updates1 = waveCRDT.joinDocument(documentId, user1)
            updates1.collect { update ->
                println("👤 $user1 received update: $update")
            }
        }
        
        // User 2 joins and adds an edge
        launch {
            val updates2 = waveCRDT.joinDocument(documentId, user2)
            updates2.collect { update ->
                println("👤 $user2 received update: $update")
            }
        }
        
        // User 3 joins and modifies a node
        launch {
            val updates3 = waveCRDT.joinDocument(documentId, user3)
            updates3.collect { update ->
                println("👤 $user3 received update: $update")
            }
        }
        
        delay(1000) // Let users join
        
        // User 1 adds a new node
        val addNodeOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.AddNode(
                nodeId = "D",
                label = "Decision",
                attributes = mapOf("shape" to "diamond")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = user1
        )
        
        println("🎯 $user1 adding node D...")
        val result1 = waveCRDT.applyOperation(documentId, user1, addNodeOp)
        println("✅ Result: $result1")
        
        delay(500)
        
        // User 2 adds an edge to the new node
        val addEdgeOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.AddEdge(
                fromId = "B",
                toId = "D",
                label = "Check",
                attributes = mapOf("style" to "dashed")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = user2
        )
        
        println("🎯 $user2 adding edge B->D...")
        val result2 = waveCRDT.applyOperation(documentId, user2, addEdgeOp)
        println("✅ Result: $result2")
        
        delay(500)
        
        // User 3 updates a node label
        val updateNodeOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.UpdateNode(
                nodeId = "A",
                newLabel = "Initialize",
                newAttributes = mapOf("color" to "green")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = user3
        )
        
        println("🎯 $user3 updating node A...")
        val result3 = waveCRDT.applyOperation(documentId, user3, updateNodeOp)
        println("✅ Result: $result3")
        
        delay(1000)
        
        // Show final state
        val finalDoc = waveCRDT.getDocument(documentId)
        println("📊 Final mermaid content:")
        println(finalDoc?.content)
        
        // Show history
        val history = waveCRDT.getDocumentHistory(documentId)
        println("📜 Document history (${history.size} versions):")
        history.forEach { snapshot ->
            println("  v${snapshot.version}: ${snapshot.commitMessage} by ${snapshot.author}")
        }
        
        // Show operation log
        val operations = waveCRDT.getOperationLog(documentId)
        println("📋 Operation log (${operations.size} operations):")
        operations.forEach { op ->
            println("  ${op.timestamp}: ${op.author} - ${op.operation}")
        }
    }
    
    /**
     * Demo: Collaborative DOT Graph Editing
     */
    suspend fun demonstrateDotCollaboration() = coroutineScope {
        println("🚀 Starting DOT Collaboration Demo")
        
        val documentId = "dot-demo-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val initialDot = """
            digraph G {
                rankdir=LR;
                A [label="Input"];
                B [label="Process"];
                C [label="Output"];
                A -> B -> C;
            }
        """.trimIndent()
        
        val document = waveCRDT.createDocument(
            id = documentId,
            type = DocumentCRDT.DocumentType.DOT,
            initialContent = initialDot,
            metadata = DocumentMetadata(
                title = "Collaborative DOT Demo",
                description = "Real-time collaborative DOT graph editing",
                author = "demo-user"
            )
        )
        
        println("📄 Created DOT document: ${document._id}")
        
        // Simulate concurrent editing
        val user1 = "designer"
        val user2 = "architect"
        
        launch {
            val updates1 = waveCRDT.joinDocument(documentId, user1)
            updates1.collect { update ->
                println("👤 $user1 received update: $update")
            }
        }
        
        launch {
            val updates2 = waveCRDT.joinDocument(documentId, user2)
            updates2.collect { update ->
                println("👤 $user2 received update: $update")
            }
        }
        
        delay(1000)
        
        // Concurrent edits
        val addNodeOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.AddNode(
                nodeId = "D",
                label = "Validate",
                attributes = mapOf("shape" to "box", "style" to "filled", "color" to "lightblue")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = user1
        )
        
        val addEdgeOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.AddEdge(
                fromId = "B",
                toId = "D",
                label = "validate",
                attributes = mapOf("color" to "red")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = user2
        )
        
        // Apply operations concurrently
        launch {
            println("🎯 $user1 adding validation node...")
            val result = waveCRDT.applyOperation(documentId, user1, addNodeOp)
            println("✅ $user1 result: $result")
        }
        
        launch {
            println("🎯 $user2 adding validation edge...")
            val result = waveCRDT.applyOperation(documentId, user2, addEdgeOp)
            println("✅ $user2 result: $result")
        }
        
        delay(2000)
        
        // Show conflict resolution
        val finalDoc = waveCRDT.getDocument(documentId)
        println("📊 Final DOT content:")
        println(finalDoc?.content)
        
        val operations = waveCRDT.getOperationLog(documentId)
        println("📋 Operations with conflict resolution:")
        operations.forEach { op ->
            op.conflictResolution?.let { resolution ->
                println("  ⚠️  Conflict resolved: ${resolution.conflictType} -> ${resolution.resolutionStrategy}")
            }
        }
    }
    
    /**
     * Demo: Git-style Branching and Merging
     */
    suspend fun demonstrateGitStyleWorkflow() = coroutineScope {
        println("🚀 Starting Git-style Workflow Demo")
        
        val documentId = "git-demo-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"
        val initialContent = """
            graph TD
                A[Main Feature] --> B[Sub Feature 1]
                A --> C[Sub Feature 2]
        """.trimIndent()
        
        val document = waveCRDT.createDocument(
            id = documentId,
            type = DocumentCRDT.DocumentType.MERMAID,
            initialContent = initialContent,
            metadata = DocumentMetadata(
                title = "Git-style Workflow Demo",
                description = "Demonstrating branching and merging",
                author = "git-demo"
            )
        )
        
        println("📄 Created main branch document: ${document._id}")
        
        // Create feature branch
        val featureBranchId = waveCRDT.createBranch(documentId, "feature-new-nodes", "developer")
        println("🌿 Created feature branch: $featureBranchId")
        
        // Make changes on feature branch
        val featureOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.AddNode(
                nodeId = "D",
                label = "New Feature",
                attributes = mapOf("color" to "green")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = "developer"
        )
        
        waveCRDT.applyOperation(featureBranchId, "developer", featureOp)
        println("✨ Added feature on branch")
        
        // Make changes on main branch
        val mainOp = DocumentOperation.GraphUpdate(
            graphId = "main",
            operation = GraphOperation.UpdateNode(
                nodeId = "A",
                newLabel = "Enhanced Main Feature",
                newAttributes = mapOf("color" to "blue")
            ),
            timestamp = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
            author = "maintainer"
        )
        
        waveCRDT.applyOperation(documentId, "maintainer", mainOp)
        println("🔧 Updated main branch")
        
        // Merge feature branch back to main
        val mergeResult = waveCRDT.mergeBranches(featureBranchId, documentId, "maintainer")
        println("🔀 Merge result: $mergeResult")
        
        // Show final merged state
        val finalDoc = waveCRDT.getDocument(documentId)
        println("📊 Merged content:")
        println(finalDoc?.content)
        
        // Show branch history
        val history = waveCRDT.getDocumentHistory(documentId)
        println("📜 Branch history:")
        history.forEach { snapshot ->
            println("  v${snapshot.version}: ${snapshot.commitMessage} (${snapshot.parentVersions})")
        }
    }
    
    /**
     * Run all demos
     */
    suspend fun runAllDemos() {
        println("🎬 Running Real-time Graph Collaboration Demos")
        println("=" * 60)
        
        demonstrateMermaidCollaboration()
        println("\n" + "=" * 60)
        
        demonstrateDotCollaboration()
        println("\n" + "=" * 60)
        
        demonstrateGitStyleWorkflow()
        println("\n" + "=" * 60)
        
        println("✅ All demos completed!")
    }
} 