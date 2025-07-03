package nexus.core.dgm.engine

import nexus.core.dgm.*
import nexus.core.dgm.services.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest

// Test-specific actual implementations for expect objects if needed by mocks indirectly
// For TimestampUtils, we'll ensure it's available as it was in InMemoryDgmStateServiceTest
internal actual object TimestampUtils {
    private var currentTime = 1000L
    actual fun now(): Long {
        currentTime += 100L
        return currentTime
    }
    fun reset(startTime: Long = 1000L) {
        currentTime = startTime
    }
}

// --- Mock Implementations ---

class MockDgmStateService : DgmStateService {
    val calls = mutableListOf<String>()
    var archiveState = mutableMapOf<ArchiveEntryId, ArchiveEntry>()
    var snapshotToReturn: CodeSnapshot? = null
    var metadataToReturn: VersionMetadata? = null
    var loadArchiveShouldThrow: Exception? = null
    var saveShouldThrow: Exception? = null

    fun reset() {
        calls.clear()
        archiveState.clear()
        snapshotToReturn = null
        metadataToReturn = null
        loadArchiveShouldThrow = null
        saveShouldThrow = null
    }

    override suspend fun loadArchive(context: CCEKContext): DgmArchive {
        calls.add("loadArchive")
        loadArchiveShouldThrow?.let { throw it }
        return Indexed.ofList(archiveState.map { Join(it.key, it.value) })
    }

    override suspend fun saveArchiveEntry(entry: ArchivedImprovement, context: CCEKContext): ArchiveEntryId {
        calls.add("saveArchiveEntry id=${entry.a.a}")
        saveShouldThrow?.let { throw it }
        val newEntryId = entry.a.a
        val candidate = entry.a.b
        val validationResult = entry.b // Not directly used to form ArchiveEntry, but part of ArchivedImprovement

        // Reconstruct ArchiveEntry as it would be stored
        val parentEntryId = candidate.a.a.a.b // candidate.task.parentEntryId
        val commitHash = "commit_for_${newEntryId.take(4)}" // Simplified
        val tags = Indexed.ofList(listOf("tag1", validationResult.a.a.lowercase()))
        val timestamp = TimestampUtils.now()
        val versionMetadata = VersionMetadata(Join(parentEntryId, commitHash), Join(tags, timestamp))
        val codeSnapshot = candidate.a.b // candidate.proposedCodeSnapshot

        archiveState[newEntryId] = ArchiveEntry(codeSnapshot, versionMetadata)
        return newEntryId
    }

    override suspend fun getEntrySnapshot(entryId: ArchiveEntryId, context: CCEKContext): CodeSnapshot? {
        calls.add("getEntrySnapshot id=$entryId")
        return snapshotToReturn ?: archiveState[entryId]?.a
    }

    override suspend fun getEntryMetadata(entryId: ArchiveEntryId, context: CCEKContext): VersionMetadata? {
        calls.add("getEntryMetadata id=$entryId")
        return metadataToReturn ?: archiveState[entryId]?.b
    }
}

class MockSolutionProposerService : SolutionProposerService {
    val calls = mutableListOf<String>()
    var candidateToReturn: ImprovementCandidate? = null
    var proposalShouldThrow: Exception? = null
    var lastTask: DgmTask? = null
    var lastParentCode: CodeSnapshot? = null


    fun reset() {
        calls.clear()
        candidateToReturn = null
        proposalShouldThrow = null
        lastTask = null
        lastParentCode = null
    }

    override suspend fun proposeSolution(task: DgmTask, parentCode: CodeSnapshot, context: CCEKContext): ImprovementCandidate {
        calls.add("proposeSolution task=${task.a.a}")
        lastTask = task
        lastParentCode = parentCode
        proposalShouldThrow?.let { throw it }
        return candidateToReturn ?: error("MockSolutionProposerService.candidateToReturn was not set for test.")
    }
}

class MockWorkspaceService : WorkspaceService {
    val calls = mutableListOf<String>()
    var workspacePathToReturn: FilePath = "/mock/workspace/default_path_0"
    var applyChangesShouldReturn: Boolean = true
    var currentSnapshotToReturn: CodeSnapshot = Indexed.empty()
    var setupShouldThrow: Exception? = null
    var applyChangesShouldThrow: Exception? = null
    var getCurrentSnapshotShouldThrow: Exception? = null
    var cleanupShouldThrow: Exception? = null
    private var counter = 0

    fun reset() {
        calls.clear()
        workspacePathToReturn = "/mock/workspace/default_path_${counter++}"
        applyChangesShouldReturn = true
        currentSnapshotToReturn = Indexed.empty()
        setupShouldThrow = null
        applyChangesShouldThrow = null
        getCurrentSnapshotShouldThrow = null
        cleanupShouldThrow = null
    }

    override suspend fun setupWorkspace(parentCode: CodeSnapshot, context: CCEKContext): FilePath {
        calls.add("setupWorkspace files=${parentCode.size}")
        setupShouldThrow?.let { throw it }
        return workspacePathToReturn
    }

    override suspend fun applyChanges(workspacePath: FilePath, changes: CodeSnapshot, context: CCEKContext): Boolean {
        calls.add("applyChanges path=$workspacePath files=${changes.size}")
        applyChangesShouldThrow?.let { throw it }
        return applyChangesShouldReturn
    }

    override suspend fun getCurrentSnapshot(workspacePath: FilePath, context: CCEKContext): CodeSnapshot {
        calls.add("getCurrentSnapshot path=$workspacePath")
        getCurrentSnapshotShouldThrow?.let { throw it }
        return currentSnapshotToReturn
    }

    override suspend fun cleanupWorkspace(workspacePath: FilePath, context: CCEKContext) {
        calls.add("cleanupWorkspace path=$workspacePath")
        cleanupShouldThrow?.let { throw it }
    }
}

class MockBenchmarkValidationService : BenchmarkValidationService {
    val calls = mutableListOf<String>()
    var validationResultToReturn: ValidationResult? = null
    var validationShouldThrow: Exception? = null
    var lastCandidate: ImprovementCandidate? = null
    var lastWorkspacePath: FilePath? = null
    var lastBenchmarkId: BenchmarkId? = null

    fun reset() {
        calls.clear()
        validationResultToReturn = null
        validationShouldThrow = null
        lastCandidate = null
        lastWorkspacePath = null
        lastBenchmarkId = null
    }

    override suspend fun validateSolution(
        candidate: ImprovementCandidate,
        workspacePath: FilePath,
        benchmarkId: BenchmarkId,
        context: CCEKContext
    ): ValidationResult {
        calls.add("validateSolution benchmark=$benchmarkId path=$workspacePath")
        lastCandidate = candidate
        lastWorkspacePath = workspacePath
        lastBenchmarkId = benchmarkId
        validationShouldThrow?.let { throw it }
        return validationResultToReturn ?: error("MockBenchmarkValidationService.validationResultToReturn was not set.")
    }
}


// --- Test Class ---

class EvolutionEngineImplTest {
    private lateinit var engine: EvolutionEngineImpl
    private val mockStateService = MockDgmStateService()
    private val mockProposerService = MockSolutionProposerService()
    private val mockWorkspaceService = MockWorkspaceService()
    private val mockValidationService = MockBenchmarkValidationService()
    private val testContext = EmptyCoroutineContext

    @BeforeTest
    fun setup() {
        TimestampUtils.reset()
        mockStateService.reset()
        mockProposerService.reset()
        mockWorkspaceService.reset()
        mockValidationService.reset()
        engine = EvolutionEngineImpl(mockStateService, mockProposerService, mockWorkspaceService, mockValidationService)
    }

    private fun createDummyCodeSnapshot(content: String = "dummy code"): CodeSnapshot {
        return Indexed.of(Join("file.kt", content))
    }

    private fun createDummyVersionMetadata(parentId: ArchiveEntryId = "p0", commit: CommitHash = "c0"): VersionMetadata {
        return VersionMetadata(Join(parentId, commit), Join(Indexed.of("tag"), TimestampUtils.now()))
    }

    private fun createDummyArchiveEntry(id: ArchiveEntryId, content: String = "code"): ArchiveEntry {
        return ArchiveEntry(createDummyCodeSnapshot(content), createDummyVersionMetadata("parent_$id", id))
    }

     private fun createDummyImprovementCandidate(
        task: DgmTask = DgmTask(Join(TaskId("t1"), ArchiveEntryId("p1")), Join(BenchmarkId("b1"), Indexed.empty())),
        proposedCode: CodeSnapshot = createDummyCodeSnapshot("proposed code")
    ): ImprovementCandidate {
        return ImprovementCandidate(
            Join(task, proposedCode),
            Join(ProposerId("test_proposer"), Indexed.of("Test rationale"))
        )
    }


    @Test
    fun initializeArchiveWhenEmpty() = runTest {
        assertTrue(mockStateService.archiveState.isEmpty())
        val initialEntryId = engine.initializeArchive(testContext)

        assertTrue(mockStateService.calls.contains("loadArchive"))
        assertTrue(mockStateService.calls.any { it.startsWith("saveArchiveEntry id=") })
        assertEquals(1, mockStateService.archiveState.size)
        assertEquals(initialEntryId, mockStateService.archiveState.keys.first())
        val entry = mockStateService.archiveState.values.first()
        assertEquals("# Initial DGM Archive Entry\nThis is the first entry in the DGM archive.", entry.a.first().b) // snapshot content
    }

    @Test
    fun initializeArchiveWhenNotEmpty() = runTest {
        val existingEntryId = "existing1"
        mockStateService.archiveState[existingEntryId] = createDummyArchiveEntry(existingEntryId)

        val resultId = engine.initializeArchive(testContext)
        assertTrue(mockStateService.calls.contains("loadArchive"))
        assertFalse(mockStateService.calls.any { it.startsWith("saveArchiveEntry") }) // No save if not empty
        assertEquals(existingEntryId, resultId) // Should return the ID of the last (only) entry
    }

    @Test
    fun selectParentEntryFromNonEmptyArchive() = runTest {
        mockStateService.archiveState["entry1"] = createDummyArchiveEntry("entry1")
        TimestampUtils.now() // ensure next entry has different timestamp if logic relied on it
        mockStateService.archiveState["entry2"] = createDummyArchiveEntry("entry2")

        val parentId = engine.selectParentEntry(mockStateService.loadArchive(testContext), testContext)
        assertEquals("entry2", parentId) // Simple "last" strategy
    }

    @Test
    fun selectParentEntryFromEmptyArchiveInitializes() = runTest {
        val initialId = engine.selectParentEntry(Indexed.empty(), testContext)
        assertTrue(mockStateService.archiveState.isNotEmpty())
        assertEquals(mockStateService.archiveState.keys.first(), initialId)
    }

    @Test
    fun selectBenchmarkEntryReturnsFromPredefinedList() = runTest {
        val benchmarkId = engine.selectBenchmarkEntry(testContext)
        assertTrue(engine.predefinedBenchmarks.contains(benchmarkId))
    }

    @Test
    fun runIterationSuccessfulPath() = runTest {
        // Setup: Initial archive entry
        val initialEntryId = engine.initializeArchive(testContext)
        mockStateService.snapshotToReturn = mockStateService.archiveState[initialEntryId]!!.a // parent snapshot

        // Mock proposer
        val proposedSnapshot = createDummyCodeSnapshot("improved code by LLM")
        val dummyTaskForCandidate = DgmTask(Join(TaskId("task_run1"), initialEntryId), Join(BenchmarkId("benchmark_A"), Indexed.empty()))
        mockProposerService.candidateToReturn = ImprovementCandidate(
            Join(dummyTaskForCandidate, proposedSnapshot),
            Join(ProposerId("llm_proposer"), Indexed.of("It's better now"))
        )

        // Mock validation
        mockValidationService.validationResultToReturn = ValidationResult(
            Join(ValidationStatus("PASSED"), Indexed.of(Join("score", 100.0))),
            "Validation successful"
        )

        mockWorkspaceService.workspacePathToReturn = "/test/ws1"

        // Execute
        val archivedImprovement = engine.runIteration(testContext)

        // Verify
        assertNotNull(archivedImprovement)
        assertTrue(mockStateService.calls.contains("loadArchive"))
        assertTrue(mockStateService.calls.any { it.startsWith("getEntrySnapshot id=$initialEntryId") })
        assertTrue(mockWorkspaceService.calls.contains("setupWorkspace files=1"))
        assertTrue(mockProposerService.calls.any { it.startsWith("proposeSolution task=") })
        assertTrue(mockWorkspaceService.calls.contains("applyChanges path=/test/ws1 files=1"))
        assertTrue(mockValidationService.calls.any { it.startsWith("validateSolution benchmark=benchmark_A") })
        assertTrue(mockStateService.calls.any { it.startsWith("saveArchiveEntry id=entry_") })
        assertEquals(2, mockStateService.archiveState.size) // Initial + new one
        assertTrue(mockWorkspaceService.calls.contains("cleanupWorkspace path=/test/ws1"))

        assertEquals(archivedImprovement.a.a, mockStateService.archiveState.keys.last()) // Check new ID
    }

    @Test
    fun runIterationValidationFails() = runTest {
        val initialEntryId = engine.initializeArchive(testContext)
        mockStateService.snapshotToReturn = mockStateService.archiveState[initialEntryId]!!.a

        mockProposerService.candidateToReturn = createDummyImprovementCandidate()
        mockValidationService.validationResultToReturn = ValidationResult(
            Join(ValidationStatus("FAILED"), Indexed.empty()), "Tests failed"
        )
        mockWorkspaceService.workspacePathToReturn = "/test/ws_fail_validate"

        val result = engine.runIteration(testContext)

        assertNull(result)
        assertFalse(mockStateService.calls.any { it.startsWith("saveArchiveEntry") }) // Should not save
        assertEquals(1, mockStateService.archiveState.size) // Only initial entry
        assertTrue(mockWorkspaceService.calls.contains("cleanupWorkspace path=/test/ws_fail_validate"))
    }

    @Test
    fun runIterationProposalReturnsErrorCandidate() = runTest {
        // Simulate SolutionProposerService indicating an error by how it forms the candidate
        // (e.g. specific ProposerId or empty changes / specific rationale)
        // The current EvolutionEngineImpl doesn't have deep logic to inspect the candidate for errors,
        // it mostly relies on validation. This test checks the flow if a "bad" candidate proceeds.
        val initialEntryId = engine.initializeArchive(testContext)
        mockStateService.snapshotToReturn = mockStateService.archiveState[initialEntryId]!!.a

        val errorCandidate = ImprovementCandidate(
            Join(DgmTask(Join(TaskId("t_err"), initialEntryId), Join(BenchmarkId("b_err"), Indexed.empty())), Indexed.empty()), // No changes
            Join(ProposerId("error_proposer"), Indexed.of("LLM failed to propose"))
        )
        mockProposerService.candidateToReturn = errorCandidate

        // Assume validation will fail for an empty/error candidate or simply return something
        mockValidationService.validationResultToReturn = ValidationResult(
            Join(ValidationStatus("ERROR"), Indexed.empty()), "Validation error due to bad proposal"
        )
        mockWorkspaceService.workspacePathToReturn = "/test/ws_prop_fail"

        val result = engine.runIteration(testContext)

        assertNull(result)
        assertTrue(mockProposerService.calls.any { it.startsWith("proposeSolution") })
        assertTrue(mockWorkspaceService.calls.contains("applyChanges path=/test/ws_prop_fail files=0")) // Applied empty changes
        assertTrue(mockValidationService.calls.any { it.startsWith("validateSolution") })
        assertFalse(mockStateService.calls.any { it.startsWith("saveArchiveEntry") })
        assertTrue(mockWorkspaceService.calls.contains("cleanupWorkspace path=/test/ws_prop_fail"))
    }


    @Test
    fun runIterationWorkspaceSetupFails() = runTest {
        engine.initializeArchive(testContext) // Ensure archive is not empty for parent selection
        mockStateService.snapshotToReturn = createDummyCodeSnapshot() // Valid snapshot

        mockWorkspaceService.setupShouldThrow = RuntimeException("Disk full")

        val result = engine.runIteration(testContext)

        assertNull(result)
        assertTrue(mockWorkspaceService.calls.contains("setupWorkspace files=1"))
        assertFalse(mockProposerService.calls.any()) // Proposal should not happen
        assertFalse(mockValidationService.calls.any()) // Validation should not happen
        // Workspace path might be null if setupWorkspace throws before returning path
        // The finally block in runIteration will try to call cleanup if workspacePath was set.
        // If it's null, cleanup won't be called with a path.
        // Let's check if it was called with null or not at all, depending on mockWorkspaceService.workspacePathToReturn
        val cleanupCalled = mockWorkspaceService.calls.any { it.startsWith("cleanupWorkspace") }
        if (mockWorkspaceService.workspacePathToReturn.isNotBlank() && mockWorkspaceService.setupShouldThrow == null) {
             // This case is tricky, if setupWorkspace sets the path then throws, finally will call cleanup
             // If it throws before setting or returning, path is null, cleanup in finally is skipped
             // For this test, setupShouldThrow means it likely won't return a path to the engine's variable
            assertFalse(cleanupCalled, "Cleanup should not be called if workspacePath was never assigned in engine")
        } else if (mockWorkspaceService.setupShouldThrow != null) {
            // If setup throws, workspacePath in engine remains null, so cleanup with path isn't called
             assertFalse(mockWorkspaceService.calls.any{it.startsWith("cleanupWorkspace path=")},
                "cleanupWorkspace with a path should not be called if setup fails early.")
        }
    }

    @Test
    fun runIterationApplyChangesFails() = runTest {
        val initialEntryId = engine.initializeArchive(testContext)
        mockStateService.snapshotToReturn = mockStateService.archiveState[initialEntryId]!!.a
        mockProposerService.candidateToReturn = createDummyImprovementCandidate()
        mockWorkspaceService.applyChangesShouldReturn = false
        mockWorkspaceService.workspacePathToReturn = "/test/ws_apply_fail"


        val result = engine.runIteration(testContext)

        assertNull(result)
        assertTrue(mockWorkspaceService.calls.contains("applyChanges path=/test/ws_apply_fail files=1"))
        assertFalse(mockValidationService.calls.any()) // Validation should not happen
        assertFalse(mockStateService.calls.any {it.startsWith("saveArchiveEntry")})
        assertTrue(mockWorkspaceService.calls.contains("cleanupWorkspace path=/test/ws_apply_fail"))
    }

    @Test
    fun runIterationSnapshotRetrievalFails() = runTest {
        engine.initializeArchive(testContext) // Ensure parent exists
        mockStateService.snapshotToReturn = null // Simulate failure to get snapshot

        val result = engine.runIteration(testContext)
        assertNull(result)
        assertTrue(mockStateService.calls.any { it.startsWith("getEntrySnapshot id=") })
        assertFalse(mockWorkspaceService.calls.any { it.startsWith("setupWorkspace") }) // Should not proceed
        // workspacePath would be null, so cleanupWorkspace(path) not called
        assertFalse(mockWorkspaceService.calls.any { it.startsWith("cleanupWorkspace path=") })
    }
}
