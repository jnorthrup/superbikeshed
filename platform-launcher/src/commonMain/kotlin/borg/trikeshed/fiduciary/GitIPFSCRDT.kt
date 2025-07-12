package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*

/**
 * Git-IPFS-CRDT implementation for distributed Wave documents
 * 
 * Combines Git commit semantics with IPFS content addressing
 * and Wave operational transformation for distributed collaboration
 */

@Serializable
data class GitWaveFlake(
    val commitHash: String,                    // Git commit hash as IPFS CID
    val parentCommits: List<String>,           // Parent commit CIDs  
    val waveOperations: List<WaveOperation>,   // OT operations in this commit
    val documentState: String,                 // Resulting document content
    val timestamp: Long,
    val author: String,
    val signature: String,                     // Digital signature
    val ipfsLinks: List<String>,               // Links to other IPFS content
    val documentType: FiduciaryDocType,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class FiduciaryDocType {
    CONTRACT,      // Legal contracts with multi-party editing
    LEDGER,        // Financial ledgers with audit requirements  
    COMPLIANCE,    // Regulatory compliance documents
    AGREEMENT,     // Multi-party agreements with consensus
    AUDIT_TRAIL    // Immutable audit histories
}

@Serializable
sealed class WaveOperation {
    abstract val position: Int
    abstract val author: String
    abstract val timestamp: Long
    abstract val operationType: OperationTaxonomy
    
    @Serializable
    data class Insert(
        override val position: Int,
        val text: String,
        override val author: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val operationType: OperationTaxonomy = OperationTaxonomy.TEXTUAL
    ) : WaveOperation()
    
    @Serializable
    data class Delete(
        override val position: Int,
        val length: Int,
        override val author: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val operationType: OperationTaxonomy = OperationTaxonomy.TEXTUAL
    ) : WaveOperation()
    
    @Serializable
    data class Retain(
        val length: Int,
        override val author: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val operationType: OperationTaxonomy = OperationTaxonomy.TEXTUAL
    ) : WaveOperation() {
        override val position: Int = 0 // Retain doesn't have position
    }
    
    @Serializable
    data class AddSignature(
        override val position: Int,
        val signature: String,
        val signedBy: String,
        override val author: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val operationType: OperationTaxonomy = OperationTaxonomy.METADATA
    ) : WaveOperation()
    
    @Serializable
    data class ProposeChange(
        override val position: Int,
        val proposalId: String,
        val changeDescription: String,
        override val author: String,
        override val timestamp: Long = System.currentTimeMillis(),
        override val operationType: OperationTaxonomy = OperationTaxonomy.CONSENSUS
    ) : WaveOperation()
}

@Serializable
enum class OperationTaxonomy {
    TEXTUAL,       // Insert, Delete, Retain
    STRUCTURAL,    // CreateSection, DeleteSection, MoveSection
    METADATA,      // AddSignature, AddTimestamp, AddApproval
    CONSENSUS      // ProposeChange, ApproveChange, RejectChange
}

enum class HostingStrategy {
    REPLICATED,    // Full replication across all nodes
    SHARDED,       // Distributed sharding
    HYBRID         // Critical parts replicated, bulk data sharded
}

/**
 * IPFS Wave Host interface for distributed document management
 */
interface IPFSWaveHost {
    suspend fun publishFlake(flake: GitWaveFlake): String
    suspend fun fetchFlake(cid: String): GitWaveFlake?
    suspend fun subscribeToDocument(docId: String): Flow<GitWaveFlake>
    suspend fun borrowOperations(fromFlake: String): Indexed<WaveOperation>
    suspend fun getHostingPeers(cid: String): Indexed<String>
}

/**
 * Git-IPFS Bridge for converting Git commits to Wave flakes
 */
class GitIPFSBridge(
    internal val ipfsHost: IPFSWaveHost
) {
    
    suspend fun commitToIPFS(
        gitCommitHash: String,
        operations: Indexed<WaveOperation>,
        parentCommits: Indexed<String> = 0 j { "" },
        documentType: FiduciaryDocType = FiduciaryDocType.AGREEMENT
    ): String {
        val flake = GitWaveFlake(
            commitHash = gitCommitHash,
            parentCommits = (0 until parentCommits.component1()).map { parentCommits.component2()(it) },
            waveOperations = (0 until operations.component1()).map { operations.component2()(it) },
            documentState = applyOperationsToDocument("", operations),
            timestamp = System.currentTimeMillis(),
            author = "git-bridge",
            signature = signFlake(operations),
            ipfsLinks = emptyList(),
            documentType = documentType
        )
        
        return ipfsHost.publishFlake(flake)
    }
    
    suspend fun borrowFromGit(
        sourceCID: String,
        operationFilter: (WaveOperation) -> Boolean = { true }
    ): Indexed<WaveOperation> {
        val borrowedOps = ipfsHost.borrowOperations(sourceCID)
        val filteredOps = mutableListOf<WaveOperation>()
        
        for (i in 0 until borrowedOps.component1()) {
            val op = borrowedOps.component2()(i)
            if (operationFilter(op)) {
                filteredOps.add(op)
            }
        }
        
        return filteredOps.size j { filteredOps[it] }
    }
    
    internal fun applyOperationsToDocument(
        baseDocument: String,
        operations: Indexed<WaveOperation>
    ): String {
        var document = baseDocument
        
        for (i in 0 until operations.component1()) {
            val op = operations.component2()(i)
            document = when (op) {
                is WaveOperation.Insert -> {
                    document.substring(0, op.position) + 
                    op.text + 
                    document.substring(op.position)
                }
                is WaveOperation.Delete -> {
                    document.substring(0, op.position) + 
                    document.substring(op.position + op.length)
                }
                is WaveOperation.Retain -> document // No change
                is WaveOperation.AddSignature -> {
                    document + "\n[SIGNATURE: ${op.signedBy} - ${op.signature}]"
                }
                is WaveOperation.ProposeChange -> {
                    document + "\n[PROPOSAL ${op.proposalId}: ${op.changeDescription}]"
                }
            }
        }
        
        return document
    }
    
    internal fun signFlake(operations: Indexed<WaveOperation>): String {
        // Simplified signature - in production use proper cryptographic signing
        val operationData = (0 until operations.component1()).joinToString("|") { i ->
            val op = operations.component2()(i)
            "${op.javaClass.simpleName}:${op.position}:${op.timestamp}"
        }
        return "SIG_${operationData.hashCode()}"
    }
}

/**
 * CRDT Consensus Engine for merging distributed operations
 */
class ConsensusCRDTEngine {
    
    fun mergeFlakes(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): GitWaveFlake {
        // Extract all operations from both sources
        val allLocalOps = extractAllOperations(localFlakes)
        val allRemoteOps = extractAllOperations(remoteFlakes)
        
        // Apply Wave OT inclusion transformation
        val (transformedLocal, transformedRemote) = inclusionTransform(allLocalOps, allRemoteOps)
        
        // Merge with causal ordering
        val mergedOps = mergeCausallyOrdered(transformedLocal, transformedRemote)
        
        // Create consensus flake
        return createConsensusFlake(localFlakes, remoteFlakes, mergedOps)
    }
    
    internal fun extractAllOperations(flakes: Indexed<GitWaveFlake>): Indexed<WaveOperation> {
        val allOps = mutableListOf<WaveOperation>()
        
        for (i in 0 until flakes.component1()) {
            val flake = flakes.component2()(i)
            allOps.addAll(flake.waveOperations)
        }
        
        return allOps.size j { allOps[it] }
    }
    
    internal fun inclusionTransform(
        local: Indexed<WaveOperation>,
        remote: Indexed<WaveOperation>
    ): Join<Indexed<WaveOperation>, Indexed<WaveOperation>> {
        
        // Simplified inclusion transformation - proper Wave OT implementation needed
        val transformedLocal = mutableListOf<WaveOperation>()
        val transformedRemote = mutableListOf<WaveOperation>()
        
        // Sort operations by timestamp for causal ordering
        val localSorted = sortOperationsByTimestamp(local)
        val remoteSorted = sortOperationsByTimestamp(remote)
        
        // Apply position adjustments based on concurrent operations
        var localOffset = 0
        var remoteOffset = 0
        
        for (i in 0 until localSorted.component1()) {
            val localOp = localSorted.component2()(i)
            val adjustedOp = adjustPositionForConcurrentOps(localOp, remoteSorted, remoteOffset)
            transformedLocal.add(adjustedOp)
        }
        
        for (i in 0 until remoteSorted.component1()) {
            val remoteOp = remoteSorted.component2()(i)
            val adjustedOp = adjustPositionForConcurrentOps(remoteOp, localSorted, localOffset)
            transformedRemote.add(adjustedOp)
        }
        
        return (transformedLocal.size j { transformedLocal[it] }) j 
               (transformedRemote.size j { transformedRemote[it] })
    }
    
    internal fun sortOperationsByTimestamp(ops: Indexed<WaveOperation>): Indexed<WaveOperation> {
        val sorted = (0 until ops.component1()).map { ops.component2()(it) }.sortedBy { it.timestamp }
        return sorted.size j { sorted[it] }
    }
    
    internal fun adjustPositionForConcurrentOps(
        op: WaveOperation,
        concurrentOps: Indexed<WaveOperation>,
        offset: Int
    ): WaveOperation {
        // Simplified position adjustment - proper OT transformation needed
        return when (op) {
            is WaveOperation.Insert -> op.copy(position = op.position + offset)
            is WaveOperation.Delete -> op.copy(position = op.position + offset)
            else -> op
        }
    }
    
    internal fun mergeCausallyOrdered(
        local: Indexed<WaveOperation>,
        remote: Indexed<WaveOperation>
    ): Indexed<WaveOperation> {
        val merged = mutableListOf<WaveOperation>()
        
        // Add all local operations
        for (i in 0 until local.component1()) {
            merged.add(local.component2()(i))
        }
        
        // Add all remote operations
        for (i in 0 until remote.component1()) {
            merged.add(remote.component2()(i))
        }
        
        // Sort by timestamp for causal ordering
        merged.sortBy { it.timestamp }
        
        return merged.size j { merged[it] }
    }
    
    internal fun createConsensusFlake(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>,
        mergedOps: Indexed<WaveOperation>
    ): GitWaveFlake {
        
        val allParents = mutableSetOf<String>()
        
        // Collect all parent commits
        for (i in 0 until localFlakes.component1()) {
            allParents.add(localFlakes.component2()(i).commitHash)
        }
        for (i in 0 until remoteFlakes.component1()) {
            allParents.add(remoteFlakes.component2()(i).commitHash)
        }
        
        return GitWaveFlake(
            commitHash = generateConsensusHash(mergedOps),
            parentCommits = allParents.toList(),
            waveOperations = (0 until mergedOps.component1()).map { mergedOps.component2()(it) },
            documentState = applyOperationsToDocument("", mergedOps),
            timestamp = System.currentTimeMillis(),
            author = "consensus_merge",
            signature = signConsensus(mergedOps),
            ipfsLinks = collectIPFSLinks(localFlakes, remoteFlakes),
            documentType = determineConsensusDocType(localFlakes, remoteFlakes)
        )
    }
    
    internal fun generateConsensusHash(ops: Indexed<WaveOperation>): String {
        val opsString = (0 until ops.component1()).joinToString("|") { i ->
            val op = ops.component2()(i)
            "${op.javaClass.simpleName}:${op.timestamp}:${op.author}"
        }
        return "CONSENSUS_${opsString.hashCode()}"
    }
    
    internal fun signConsensus(ops: Indexed<WaveOperation>): String {
        return "CONSENSUS_SIG_${ops.component1()}_${System.currentTimeMillis()}"
    }
    
    internal fun collectIPFSLinks(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): List<String> {
        val allLinks = mutableSetOf<String>()
        
        for (i in 0 until localFlakes.component1()) {
            allLinks.addAll(localFlakes.component2()(i).ipfsLinks)
        }
        for (i in 0 until remoteFlakes.component1()) {
            allLinks.addAll(remoteFlakes.component2()(i).ipfsLinks)
        }
        
        return allLinks.toList()
    }
    
    internal fun determineConsensusDocType(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): FiduciaryDocType {
        // Simple heuristic - use most common document type
        val types = mutableListOf<FiduciaryDocType>()
        
        for (i in 0 until localFlakes.component1()) {
            types.add(localFlakes.component2()(i).documentType)
        }
        for (i in 0 until remoteFlakes.component1()) {
            types.add(remoteFlakes.component2()(i).documentType)
        }
        
        return types.groupBy { it }.maxByOrNull { it.value.size }?.key 
            ?: FiduciaryDocType.AGREEMENT
    }
    
    internal fun applyOperationsToDocument(
        baseDocument: String,
        operations: Indexed<WaveOperation>
    ): String {
        var document = baseDocument
        
        for (i in 0 until operations.component1()) {
            val op = operations.component2()(i)
            document = when (op) {
                is WaveOperation.Insert -> {
                    if (op.position <= document.length) {
                        document.substring(0, op.position) + 
                        op.text + 
                        document.substring(op.position)
                    } else {
                        document + op.text
                    }
                }
                is WaveOperation.Delete -> {
                    if (op.position < document.length) {
                        val endPos = (op.position + op.length).coerceAtMost(document.length)
                        document.substring(0, op.position) + 
                        document.substring(endPos)
                    } else {
                        document
                    }
                }
                is WaveOperation.Retain -> document
                is WaveOperation.AddSignature -> {
                    document + "\n[SIGNATURE: ${op.signedBy}]"
                }
                is WaveOperation.ProposeChange -> {
                    document + "\n[PROPOSAL: ${op.changeDescription}]"
                }
            }
        }
        
        return document
    }
}

/**
 * Distributed Document Manager combining Git, IPFS, and CRDT
 */
class DistributedDocumentManager(
    internal val ipfsHost: IPFSWaveHost,
    internal val gitBridge: GitIPFSBridge,
    internal val crdt: ConsensusCRDTEngine
) {
    
    suspend fun createDocument(
        initialContent: String,
        docType: FiduciaryDocType,
        author: String
    ): String {
        val initialOp = WaveOperation.Insert(
            position = 0,
            text = initialContent,
            author = author
        )
        
        return gitBridge.commitToIPFS(
            gitCommitHash = "INITIAL_${System.currentTimeMillis()}",
            operations = 1 j { initialOp },
            documentType = docType
        )
    }
    
    suspend fun collaborateOnDocument(
        docCID: String,
        newOperations: Indexed<WaveOperation>
    ): String {
        val existingFlake = ipfsHost.fetchFlake(docCID)
            ?: throw IllegalArgumentException("Document not found: $docCID")
        
        val mergedFlake = crdt.mergeFlakes(
            localFlakes = 1 j { existingFlake },
            remoteFlakes = 1 j { 
                existingFlake.copy(
                    waveOperations = (0 until newOperations.component1()).map { newOperations.component2()(it) },
                    timestamp = System.currentTimeMillis()
                )
            }
        )
        
        return ipfsHost.publishFlake(mergedFlake)
    }
    
    suspend fun borrowFromRepository(
        sourceCID: String,
        targetCID: String,
        borrowFilter: (WaveOperation) -> Boolean = { true }
    ): String {
        val borrowedOps = gitBridge.borrowFromGit(sourceCID, borrowFilter)
        return collaborateOnDocument(targetCID, borrowedOps)
    }
    
    suspend fun subscribeToDocumentUpdates(docCID: String): Flow<GitWaveFlake> {
        return ipfsHost.subscribeToDocument(docCID)
    }
}