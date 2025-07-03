package nexus.core.dgm.engine

import nexus.core.dgm.*
import nexus.core.dgm.services.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlin.random.Random // For random selection strategies

class EvolutionEngineImpl(
    private val dgmStateService: DgmStateService,
    private val solutionProposerService: SolutionProposerService,
    private val workspaceService: WorkspaceService,
    private val benchmarkValidationService: BenchmarkValidationService
) : EvolutionEngine {

    private val predefinedBenchmarks = listOf("benchmark_A", "benchmark_B", "benchmark_C_polyglot_python")

    override suspend fun initializeArchive(context: CCEKContext): ArchiveEntryId {
        val currentArchive = dgmStateService.loadArchive(context)
        if (currentArchive.isEmpty()) {
            println("EvolutionEngine: Archive is empty. Initializing with a basic entry.")
            val initialCommitHash = "initial_commit_00000000"
            val initialEntryId: ArchiveEntryId = initialCommitHash

            val readmeFile = Join<FilePath, FileContent>("README.md", "# Initial DGM Archive Entry\nThis is the first entry in the DGM archive.")
            val initialCodeSnapshot = Indexed.of(readmeFile)

            val parentEntryId: ArchiveEntryId = "null_parent" // Or some other sentinel
            val tags = Indexed.of("initialization")
            val timestamp = TimestampUtils.now() // Assuming TimestampUtils is accessible

            val initialVersionMetadata = VersionMetadata(
                Join(parentEntryId, initialCommitHash),
                Join(tags, timestamp)
            )

            val initialArchiveEntry = ArchiveEntry(initialCodeSnapshot, initialVersionMetadata)

            // To save this, we'd typically have a simpler "force save" or an "initial entry" concept
            // For now, let's simulate an ArchivedImprovement to fit the DgmStateService interface.
            // This is a bit of a workaround due to current DgmStateService.saveArchiveEntry signature.
            val dummyTask = DgmTask(
                Join(TaskId("init_task"), parentEntryId),
                Join(BenchmarkId("init_benchmark"), Indexed.empty())
            )
            val dummyCandidate = ImprovementCandidate(
                Join(dummyTask, initialCodeSnapshot),
                Join(ProposerId("engine_init"), Indexed.of("Initial archive setup."))
            )
            val dummyValidation = ValidationResult(
                Join(ValidationStatus("PASSED"), Indexed.empty<BenchmarkScore>()),
                "Initialization successful."
            )

            val initialArchivedImprovement = ArchivedImprovement(
                Join(initialEntryId, dummyCandidate), // newEntryId, original candidate
                dummyValidation
            )

            return dgmStateService.saveArchiveEntry(initialArchivedImprovement, context)
        }
        // If archive is not empty, we can return the ID of the latest entry or a specific one.
        // For simplicity, let's assume if not empty, it's "initialized".
        // A more robust approach might return the latest entry ID.
        return currentArchive.lastOrNull()?.a ?: error("Archive not empty but could not get last entry ID.")
    }

    override suspend fun selectParentEntry(archive: DgmArchive, context: CCEKContext): ArchiveEntryId {
        if (archive.isEmpty()) {
            println("EvolutionEngine: Archive empty, initializing to select a parent.")
            return initializeArchive(context) // Initialize and return the first entry ID
        }
        // Simple strategy: select the latest entry (last one in the Indexed)
        // A more complex strategy could involve scores, tags, randomness, etc.
        val lastEntry = archive.lastOrNull() ?: error("Archive is not empty but could not retrieve the last entry.")
        println("EvolutionEngine: Selected parent entry: ${lastEntry.a}")
        return lastEntry.a // ArchiveEntryId is the first part of the Join
    }

    override suspend fun selectBenchmarkEntry(context: CCEKContext): BenchmarkId {
        // Simple strategy: pick randomly from a predefined list
        val selectedBenchmark = predefinedBenchmarks.random()
        println("EvolutionEngine: Selected benchmark: $selectedBenchmark")
        return selectedBenchmark
    }

    override suspend fun runIteration(context: CCEKContext): ArchivedImprovement? {
        println("EvolutionEngine: Starting new iteration...")
        var workspacePath: FilePath? = null
        try {
            val currentArchive = dgmStateService.loadArchive(context)
            val parentEntryId = selectParentEntry(currentArchive, context)
            val benchmarkId = selectBenchmarkEntry(context)

            val parentSnapshot = dgmStateService.getEntrySnapshot(parentEntryId, context)
            if (parentSnapshot == null) {
                println("EvolutionEngine: Error - Could not retrieve snapshot for parent entry $parentEntryId.")
                return null
            }

            val dgmTask = DgmTask(
                Join(TaskId("task_${Random.nextInt(1000, 9999)}"), parentEntryId),
                Join(benchmarkId, Indexed.empty()) // Empty parameters for now
            )
            println("EvolutionEngine: Created DGM Task: ${dgmTask.a.a} for parent ${dgmTask.a.b} on benchmark ${dgmTask.b.a}")

            workspacePath = workspaceService.setupWorkspace(parentSnapshot, context)
            println("EvolutionEngine: Workspace setup at $workspacePath")

            val improvementCandidate = solutionProposerService.proposeSolution(dgmTask, parentSnapshot, context)
            println("EvolutionEngine: Proposed solution for task ${dgmTask.a.a} by ${improvementCandidate.b.a}")

            // The proposed changes are in improvementCandidate.a.b (CodeSnapshot)
            val changesApplied = workspaceService.applyChanges(workspacePath, improvementCandidate.a.b, context)
            if (!changesApplied) {
                println("EvolutionEngine: Error - Failed to apply changes to workspace $workspacePath.")
                return null // Or handle more gracefully
            }
            println("EvolutionEngine: Applied proposed changes to workspace.")

            val validationResult = benchmarkValidationService.validateSolution(
                improvementCandidate,
                workspacePath,
                benchmarkId,
                context
            )
            println("EvolutionEngine: Validation result for task ${dgmTask.a.a}: ${validationResult.a.a}")

            if (validationResult.a.a.equals("PASSED", ignoreCase = true)) { // Check status from ValidationResult
                // Potentially add more checks here, e.g., if score actually improved.
                println("EvolutionEngine: Validation PASSED. Archiving improvement.")
                val newArchiveEntryId = "entry_${Random.nextInt(10000, 99999)}_${benchmarkId.replace("-","_")}"

                // The ImprovementCandidate already contains the DgmTask and the proposed CodeSnapshot
                // The ArchivedImprovement wraps this with the new ID and the validation result
                val archivedImprovement = ArchivedImprovement(
                    Join(newArchiveEntryId, improvementCandidate),
                    validationResult
                )
                dgmStateService.saveArchiveEntry(archivedImprovement, context)
                println("EvolutionEngine: Successfully archived improvement as $newArchiveEntryId.")
                return archivedImprovement
            } else {
                println("EvolutionEngine: Validation did not pass (${validationResult.a.a}). No improvement archived.")
                return null
            }

        } catch (e: Exception) {
            println("EvolutionEngine: Error during iteration: ${e.message}")
            e.printStackTrace() // Or use a proper logger
            return null
        } finally {
            workspacePath?.let {
                try {
                    workspaceService.cleanupWorkspace(it, context)
                    println("EvolutionEngine: Workspace $it cleaned up.")
                } catch (e: Exception) {
                    println("EvolutionEngine: Error cleaning up workspace $it: ${e.message}")
                }
            }
            println("EvolutionEngine: Iteration finished.")
        }
    }
}
