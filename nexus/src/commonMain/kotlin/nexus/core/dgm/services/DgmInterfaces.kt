package nexus.core.dgm.services

import nexus.core.dgm.*
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Series // Assuming CCEKContext might be a TrikeShed type or a basic CoroutineContext

// For now, CCEKContext can be a placeholder. It would typically be a more complex
// type alias using Join, potentially from a dedicated CCEK definition file.
// If it's just CoroutineContext for service lookup, that's fine too.
// For this task, we'll assume it's passed around and services might extract
// needed sub-contexts or specific service keys from it.
typealias CCEKContext = CoroutineContext // Placeholder, replace with actual CCEK context type if available

interface EvolutionEngine {
    suspend fun initializeArchive(context: CCEKContext): ArchiveEntryId // Or return initial ArchiveEntryId
    suspend fun runIteration(context: CCEKContext): ArchivedImprovement? // Returns new archive entry if successful
    suspend fun selectParentEntry(archive: DgmArchive, context: CCEKContext): ArchiveEntryId
    suspend fun selectBenchmarkEntry(context: CCEKContext): BenchmarkId // Could be more complex based on strategy

    companion object Key : CoroutineContext.Key<EvolutionEngine>
}

interface SolutionProposerService {
    // Proposes changes to the codebase for a given task
    suspend fun proposeSolution(task: DgmTask, parentCode: CodeSnapshot, context: CCEKContext): ImprovementCandidate

    companion object Key : CoroutineContext.Key<SolutionProposerService>
}

interface WorkspaceService {
    // Manages the working directory for DGM operations
    suspend fun setupWorkspace(parentCode: CodeSnapshot, context: CCEKContext): FilePath // Returns root path of workspace
    suspend fun applyChanges(workspacePath: FilePath, changes: CodeSnapshot, context: CCEKContext): Boolean
    suspend fun getCurrentSnapshot(workspacePath: FilePath, context: CCEKContext): CodeSnapshot
    suspend fun cleanupWorkspace(workspacePath: FilePath, context: CCEKContext)

    companion object Key : CoroutineContext.Key<WorkspaceService>
}

interface BenchmarkValidationService {
    // Validates a solution against a benchmark
    suspend fun validateSolution(candidate: ImprovementCandidate, workspacePath: FilePath, benchmarkId: BenchmarkId, context: CCEKContext): ValidationResult

    companion object Key : CoroutineContext.Key<BenchmarkValidationService>
}

interface DgmStateService { // Archive Management
    suspend fun loadArchive(context: CCEKContext): DgmArchive
    suspend fun saveArchiveEntry(entry: ArchivedImprovement, context: CCEKContext): ArchiveEntryId
    suspend fun getEntrySnapshot(entryId: ArchiveEntryId, context: CCEKContext): CodeSnapshot?
    suspend fun getEntryMetadata(entryId: ArchiveEntryId, context: CCEKContext): VersionMetadata?

    companion object Key : CoroutineContext.Key<DgmStateService>
}
