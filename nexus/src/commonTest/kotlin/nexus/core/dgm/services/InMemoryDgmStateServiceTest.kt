package nexus.core.dgm.services

import nexus.core.dgm.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlinx.coroutines.test.runTest // For running suspend functions in tests

// Actual implementation of TimestampUtils for commonTest to make tests runnable
// In a real KMP project, this might go into a test utility source set or be handled by platform-specific test runners.
internal actual object TimestampUtils {
    private var currentTime = 1000L // Start with a known time for predictability
    actual fun now(): Long {
        currentTime += 100L // Increment time deterministically for tests
        return currentTime
    }
    fun reset() { // Helper for tests
        currentTime = 1000L
    }
}


class InMemoryDgmStateServiceTest {

    private lateinit var stateService: InMemoryDgmStateService
    private val testContext = EmptyCoroutineContext // Simple context for tests

    @BeforeTest
    fun setup() {
        TimestampUtils.reset() // Reset time for each test
        stateService = InMemoryDgmStateService()
    }

    @Test
    fun initialArchiveIsEmpty() = runTest {
        val archive = stateService.loadArchive(testContext)
        assertTrue(archive.isEmpty(), "Initially, the archive should be empty.")
    }

    private fun createDummyArchivedImprovement(
        archiveId: ArchiveEntryId,
        parentEntryId: ArchiveEntryId = "parent_${archiveId}",
        taskId: TaskId = "task_${archiveId}",
        proposerId: ProposerId = "proposer_test",
        benchmarkId: BenchmarkId = "benchmark_test",
        filePath: FilePath = "file.kt",
        fileContent: FileContent = "content for $archiveId",
        validationStatus: ValidationStatus = "PASSED",
        metricName: String = "score",
        metricValue: Double = 1.0,
        logOutput: LogOutput = "Log for $archiveId"
    ): ArchivedImprovement {
        val codeSnapshot = Indexed.of(Join(filePath, fileContent))
        val dgmTask = DgmTask(
            Join(taskId, parentEntryId),
            Join(benchmarkId, Indexed.empty<String>())
        )
        val improvementCandidate = ImprovementCandidate(
            Join(dgmTask, codeSnapshot),
            Join(proposerId, Indexed.of("Rationale for $archiveId"))
        )
        val validationResult = ValidationResult(
            Join(validationStatus, Indexed.of(Join(metricName, metricValue))),
            logOutput
        )
        return ArchivedImprovement(
            Join(archiveId, improvementCandidate),
            validationResult
        )
    }

    @Test
    fun saveAndLoadSingleArchiveEntry() = runTest {
        val entryId = "entry1"
        val archivedImprovement = createDummyArchivedImprovement(entryId)

        val savedEntryId = stateService.saveArchiveEntry(archivedImprovement, testContext)
        assertEquals(entryId, savedEntryId, "Saved entry ID should match the input.")

        val archive = stateService.loadArchive(testContext)
        assertEquals(1, archive.size, "Archive should contain one entry after saving.")
        val loadedEntryJoin = archive.firstOrNull()
        assertNotNull(loadedEntryJoin, "Loaded entry join should not be null.")
        assertEquals(entryId, loadedEntryJoin.a, "ArchiveEntryId in loaded entry should match.")

        val loadedArchiveEntry = loadedEntryJoin.b
        assertNotNull(loadedArchiveEntry, "Loaded ArchiveEntry should not be null.")

        // Check code snapshot
        val codeSnapshot = loadedArchiveEntry.a
        assertEquals(1, codeSnapshot.size, "CodeSnapshot should have one file.")
        assertEquals("file.kt", codeSnapshot.first().a, "File path in snapshot is incorrect.")
        assertEquals("content for entry1", codeSnapshot.first().b, "File content in snapshot is incorrect.")

        // Check metadata (simplified check)
        val metadata = loadedArchiveEntry.b
        assertEquals("parent_entry1", metadata.a.a, "Parent entry ID in metadata is incorrect.")
        assertTrue(metadata.b.a.any { it == "improvement" }, "Tags should include 'improvement'.")
    }

    @Test
    fun getEntrySnapshotForValidId() = runTest {
        val entryId = "entry_snapshot_test"
        val archivedImprovement = createDummyArchivedImprovement(entryId, filePath = "test.txt", fileContent = "snapshot content")
        stateService.saveArchiveEntry(archivedImprovement, testContext)

        val snapshot = stateService.getEntrySnapshot(entryId, testContext)
        assertNotNull(snapshot, "Snapshot should not be null for a valid ID.")
        assertEquals(1, snapshot.size, "Snapshot should contain one file.")
        assertEquals("test.txt", snapshot.first().a)
        assertEquals("snapshot content", snapshot.first().b)
    }

    @Test
    fun getEntrySnapshotForInvalidId() = runTest {
        val snapshot = stateService.getEntrySnapshot("non_existent_id", testContext)
        assertNull(snapshot, "Snapshot should be null for an invalid ID.")
    }

    @Test
    fun getEntryMetadataForValidId() = runTest {
        val entryId = "entry_metadata_test"
        val parentId = "parent_for_metadata"
        val commitHashInMeta = "simulated_hash_for_${entryId.take(8)}" // Matches logic in InMemoryDgmStateService
        val archivedImprovement = createDummyArchivedImprovement(entryId, parentEntryId = parentId)
        stateService.saveArchiveEntry(archivedImprovement, testContext)

        val metadata = stateService.getEntryMetadata(entryId, testContext)
        assertNotNull(metadata, "Metadata should not be null for a valid ID.")
        assertEquals(parentId, metadata.a.a, "Metadata parent ID mismatch.")
        assertEquals(commitHashInMeta, metadata.a.b, "Metadata commit hash mismatch.")
        assertTrue(metadata.b.a.any { it == "passed" }, "Metadata tags mismatch.")
        assertEquals(TimestampUtils.now() - 100L, metadata.b.b, "Timestamp in metadata mismatch") // -100L because now() increments
    }

    @Test
    fun getEntryMetadataForInvalidId() = runTest {
        val metadata = stateService.getEntryMetadata("non_existent_id", testContext)
        assertNull(metadata, "Metadata should be null for an invalid ID.")
    }

    @Test
    fun saveMultipleEntriesAndLoadArchive() = runTest {
        val entry1 = createDummyArchivedImprovement("entryA")
        val entry2 = createDummyArchivedImprovement("entryB", parentEntryId = "entryA")

        stateService.saveArchiveEntry(entry1, testContext)
        stateService.saveArchiveEntry(entry2, testContext)

        val archive = stateService.loadArchive(testContext)
        assertEquals(2, archive.size, "Archive should contain two entries.")

        val entryAExists = archive.any { it.a == "entryA" }
        val entryBExists = archive.any { it.a == "entryB" }
        assertTrue(entryAExists, "Entry A should exist in the archive.")
        assertTrue(entryBExists, "Entry B should exist in the archive.")
    }

    @Test
    fun overwritingEntryUpdatesIt() = runTest {
        val entryId = "overwrite_test"
        val initialImprovement = createDummyArchivedImprovement(entryId, fileContent = "initial content")
        stateService.saveArchiveEntry(initialImprovement, testContext)

        val initialSnapshot = stateService.getEntrySnapshot(entryId, testContext)
        assertEquals("initial content", initialSnapshot?.firstOrNull()?.b)

        val updatedImprovement = createDummyArchivedImprovement(entryId, fileContent = "updated content")
        stateService.saveArchiveEntry(updatedImprovement, testContext) // This should overwrite

        val archive = stateService.loadArchive(testContext)
        assertEquals(1, archive.size, "Archive should still contain one entry after overwrite.")

        val updatedSnapshot = stateService.getEntrySnapshot(entryId, testContext)
        assertNotNull(updatedSnapshot)
        assertEquals("updated content", updatedSnapshot.firstOrNull()?.b, "File content should be updated.")

        val updatedMetadata = stateService.getEntryMetadata(entryId, testContext)
        assertNotNull(updatedMetadata)
        // Timestamp should be different if `now()` is called again in save.
        // The dummy TimestampUtils increments time, so the new timestamp will be higher.
        assertEquals(TimestampUtils.now() - 100L, updatedMetadata.b.b)
    }
}
