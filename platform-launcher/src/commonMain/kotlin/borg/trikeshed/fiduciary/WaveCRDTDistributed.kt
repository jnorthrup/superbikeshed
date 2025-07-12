package borg.trikeshed.fiduciary

import borg.trikeshed.lib.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.datetime.*

/**
 * Distributed Wave CRDT implementation integrating with Git-IPFS-CRDT taxonomy.
 * 
 * Uses MetaSeries<A,T> = Join<A, (A) -> T> for efficient projection-based access
 * to distributed operations across Git commits and IPFS content addressing.
 */

/**
 * Git-Wave Flake following the taxonomy specification
 */
@Serializable
data class GitWaveFlake(
    val commitHash: String,                    // Git commit hash as IPFS CID
    val parentCommits: Indexed<String>,        // Parent commit CIDs  
    val waveOps: Indexed<SerializedWaveOp>,    // OT operations in this commit
    val documentState: String,                 // Resulting document content
    val timestamp: Instant,
    val author: ParticipantId,
    val signature: ByteArray,
    val ipfsLinks: Indexed<String>             // Links to other IPFS content
) {
    fun toMetaSeries(): MetaSeries<String, SerializedWaveOp> = 
        commitHash j { _: String -> 
            // Project from commit hash to aggregated operations
            waveOps.toList().firstOrNull() ?: SerializedWaveOp.Retain(0)
        }
    
    fun operationsAsMetaSeries(): MetaSeries<Int, SerializedWaveOp> = 
        waveOps.component1() j waveOps.component2()
}

/**
 * CRDT Merge Strategy for distributed Wave operations
 */
class DistributedWaveCRDTMerge(
    internal val convergence: WaveCRDTConvergence = WaveCRDTConvergence()
) {
    
    /**
     * Merge borrowed commits using MetaSeries projections
     */
    fun mergeBorrowedCommits(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): GitWaveFlake {
        // Create MetaSeries for efficient operation access
        val localOps: MetaSeries<Int, SerializedWaveOp> = createOperationSeries(localFlakes)
        val remoteOps: MetaSeries<Int, SerializedWaveOp> = createOperationSeries(remoteFlakes)
        
        // Merge operations with consensus transformation
        val mergedOps = applyWaveConsensus(localOps, remoteOps)
        
        // Compute resulting document state
        val documentState = computeDocumentState(mergedOps)
        
        return GitWaveFlake(
            commitHash = generateCID(mergedOps),
            parentCommits = collectParentCommits(localFlakes, remoteFlakes),
            waveOps = mergedOps,
            documentState = documentState,
            timestamp = Clock.System.now(),
            author = ParticipantId("merge_consensus"),
            signature = signMergeResult(mergedOps, documentState),
            ipfsLinks = collectIPFSLinks(localFlakes, remoteFlakes)
        )
    }
    
    /**
     * Create MetaSeries projection from flakes to operations
     */
    internal fun createOperationSeries(flakes: Indexed<GitWaveFlake>): MetaSeries<Int, SerializedWaveOp> {
        val allOps = flakes.flatMap { flake -> flake.waveOps.toList() }
        return allOps.size j { i: Int -> allOps[i] }
    }
    
    /**
     * Apply Wave consensus transformation using operational transformation
     */
    internal fun applyWaveConsensus(
        localOps: MetaSeries<Int, SerializedWaveOp>,
        remoteOps: MetaSeries<Int, SerializedWaveOp>
    ): Indexed<SerializedWaveOp> {
        val localList = (0 until localOps.component1()).map { localOps.component2()(it) }
        val remoteList = (0 until remoteOps.component1()).map { remoteOps.component2()(it) }
        
        // Transform each remote operation against all local operations
        val transformedRemote = remoteList.map { remoteOp ->
            localList.fold(remoteOp) { acc, localOp ->
                val transform = convergence.transform(
                    acc.toWaveOp(),
                    localOp.toWaveOp()
                )
                (transform.component1() as WaveDocumentOp).toSerializable()
            }
        }
        
        // Combine local and transformed remote operations
        val merged = localList + transformedRemote
        return merged.size j { i: Int -> merged[i] }
    }
    
    internal fun computeDocumentState(operations: Indexed<SerializedWaveOp>): String {
        var state = WaveDocumentState("")
        
        operations.toList().forEach { op ->
            when (val result = op.toWaveOp().apply(state)) {
                is Either.Left -> state = result.value
                is Either.Right -> {} // Skip failed operations in merge
            }
        }
        
        return state.content
    }
    
    internal fun generateCID(operations: Indexed<SerializedWaveOp>): String {
        val content = operations.toList().joinToString { it.toString() }
        return "Qm${content.hashCode().toString(16).padStart(44, '0')}"
    }
    
    internal fun collectParentCommits(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): Indexed<String> {
        val parents = (localFlakes.toList() + remoteFlakes.toList())
            .map { it.commitHash }
            .distinct()
        return parents.size j { i: Int -> parents[i] }
    }
    
    internal fun signMergeResult(operations: Indexed<SerializedWaveOp>, state: String): ByteArray {
        val content = "${operations.toList().hashCode()}-${state.hashCode()}"
        return content.encodeToByteArray()
    }
    
    internal fun collectIPFSLinks(
        localFlakes: Indexed<GitWaveFlake>,
        remoteFlakes: Indexed<GitWaveFlake>
    ): Indexed<String> {
        val links = (localFlakes.flatMap { it.ipfsLinks.toList() } +
                    remoteFlakes.flatMap { it.ipfsLinks.toList() })
            .distinct()
        return links.size j { i: Int -> links[i] }
    }
}

/**
 * Distributed Wave Session with Git-IPFS backing
 */
class DistributedWaveSession(
    val sessionId: String,
    internal val crdtEngine: WaveCRDTEngine = WaveCRDTEngine(),
    internal val merger: DistributedWaveCRDTMerge = DistributedWaveCRDTMerge()
) {
    internal val flakeHistory = mutableListOf<GitWaveFlake>()
    internal val _flakeFlow = MutableSharedFlow<GitWaveFlake>()
    val flakeFlow: Flow<GitWaveFlake> = _flakeFlow.asSharedFlow()
    
    /**
     * Create a Git-Wave flake from current operations
     */
    suspend fun createFlake(
        documentId: DocumentId,
        waveletId: WaveletId,
        author: ParticipantId
    ): GitWaveFlake? {
        val doc = crdtEngine.getDocument(documentId) ?: return null
        val wavelet = doc.getWavelet(waveletId) ?: return null
        
        val operations = wavelet.operations.toList().map { it.operation }
        val parentCommits = if (flakeHistory.isEmpty()) {
            0 j { throw IndexOutOfBoundsException() }
        } else {
            flakeHistory.takeLast(2).map { it.commitHash }.toIdx()
        }
        
        val flake = GitWaveFlake(
            commitHash = generateCommitHash(operations),
            parentCommits = parentCommits,
            waveOps = operations.toIdx(),
            documentState = doc.state.content,
            timestamp = Clock.System.now(),
            author = author,
            signature = signFlake(operations, author),
            ipfsLinks = 0 j { throw IndexOutOfBoundsException() }
        )
        
        flakeHistory.add(flake)
        _flakeFlow.emit(flake)
        return flake
    }
    
    /**
     * Borrow operations from a Git repository
     */
    suspend fun borrowFromGit(
        repoUrl: String,
        commitRange: String
    ): Indexed<SerializedWaveOp> {
        // Simulate extracting Wave operations from Git commits
        // In real implementation, this would parse Git diffs and extract operations
        return 0 j { throw IndexOutOfBoundsException() }
    }
    
    /**
     * Merge remote flakes with local state
     */
    suspend fun mergeRemoteFlakes(
        documentId: DocumentId,
        remoteFlakes: Indexed<GitWaveFlake>
    ): Either<GitWaveFlake, OperationException> {
        val localFlakes = flakeHistory.toIdx()
        
        if (localFlakes.component1() == 0) {
            // No local flakes, just apply remote
            return applyRemoteFlakes(documentId, remoteFlakes)
        }
        
        val mergedFlake = merger.mergeBorrowedCommits(localFlakes, remoteFlakes)
        
        // Apply merged operations to document
        val doc = crdtEngine.getDocument(documentId)
            ?: return Either.Right(OperationException("Document not found"))
        
        var state = doc.state
        mergedFlake.waveOps.toList().forEach { op ->
            when (val result = op.toWaveOp().apply(state)) {
                is Either.Left -> state = result.value
                is Either.Right -> return Either.Right(result.value)
            }
        }
        
        flakeHistory.add(mergedFlake)
        _flakeFlow.emit(mergedFlake)
        return Either.Left(mergedFlake)
    }
    
    internal suspend fun applyRemoteFlakes(
        documentId: DocumentId,
        remoteFlakes: Indexed<GitWaveFlake>
    ): Either<GitWaveFlake, OperationException> {
        // Apply all remote operations in order
        remoteFlakes.toList().forEach { flake ->
            flakeHistory.add(flake)
            _flakeFlow.emit(flake)
        }
        
        return Either.Left(remoteFlakes.component2()(remoteFlakes.component1() - 1))
    }
    
    internal fun generateCommitHash(operations: List<SerializedWaveOp>): String {
        val content = operations.joinToString { it.toString() }
        return "commit-${content.hashCode().toString(16)}"
    }
    
    internal fun signFlake(operations: List<SerializedWaveOp>, author: ParticipantId): ByteArray {
        val content = "${operations.hashCode()}-${author.value}"
        return content.encodeToByteArray()
    }
}

/**
 * IPFS integration for Wave documents
 */
interface IPFSWaveHost {
    suspend fun publishFlake(flake: GitWaveFlake): String
    suspend fun fetchFlake(cid: String): GitWaveFlake?
    suspend fun subscribeToDocument(docId: String): Flow<GitWaveFlake>
    suspend fun borrowOperations(fromFlake: String): Indexed<SerializedWaveOp>
}

/**
 * Consensus CRDT Engine for multi-party agreement
 */
class ConsensusCRDTEngine(
    internal val requiredApprovals: Int = 2
) {
    internal val approvals = mutableMapOf<String, MutableSet<ParticipantId>>()
    
    suspend fun proposeOperation(
        flake: GitWaveFlake,
        proposer: ParticipantId
    ): ConsensusProposal {
        val proposalId = "proposal-${flake.commitHash}"
        approvals[proposalId] = mutableSetOf(proposer)
        
        return ConsensusProposal(
            id = proposalId,
            flake = flake,
            proposer = proposer,
            approvals = setOf(proposer),
            requiredApprovals = requiredApprovals,
            status = ConsensusStatus.PENDING
        )
    }
    
    suspend fun approveProposal(
        proposalId: String,
        approver: ParticipantId
    ): ConsensusProposal? {
        val approvalSet = approvals[proposalId] ?: return null
        approvalSet.add(approver)
        
        val status = if (approvalSet.size >= requiredApprovals) {
            ConsensusStatus.APPROVED
        } else {
            ConsensusStatus.PENDING
        }
        
        return ConsensusProposal(
            id = proposalId,
            flake = GitWaveFlake(
                commitHash = proposalId,
                parentCommits = 0 j { throw IndexOutOfBoundsException() },
                waveOps = 0 j { throw IndexOutOfBoundsException() },
                documentState = "",
                timestamp = Clock.System.now(),
                author = approver,
                signature = ByteArray(0),
                ipfsLinks = 0 j { throw IndexOutOfBoundsException() }
            ),
            proposer = approver,
            approvals = approvalSet.toSet(),
            requiredApprovals = requiredApprovals,
            status = status
        )
    }
}

@Serializable
data class ConsensusProposal(
    val id: String,
    val flake: GitWaveFlake,
    val proposer: ParticipantId,
    val approvals: Set<ParticipantId>,
    val requiredApprovals: Int,
    val status: ConsensusStatus
)

@Serializable
enum class ConsensusStatus {
    PENDING,
    APPROVED,
    REJECTED
}

/**
 * Extension to convert List to Indexed using MetaSeries pattern
 */
fun <T> List<T>.toIdx(): Indexed<T> = size j { i: Int -> this[i] }