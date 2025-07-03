package nexus.core.dgm

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Indexed

// Basic typealiases from the design
typealias FilePath = String
typealias FileContent = String
typealias TaskId = String
typealias BenchmarkId = String
typealias CommitHash = String
typealias Timestamp = Long
typealias ProposerId = String

/**
 * Unique identifier for an entry in the DGM archive.
 * Can be a simple String (e.g., commit hash) or a more complex structure if versioning within an ID is needed.
 */
typealias ArchiveEntryId = String // Simplified as per design doc, can be Join<String, Int> for versioning

/**
 * Metadata associated with a version of code in the DGM archive.
 * Join<
 *     Join<ArchiveEntryId, CommitHash>, // ParentEntryId (String), CommitHash (String)
 *     Join<Indexed<String>, Timestamp>    // Tags, Timestamp (Long)
 * >
 */
typealias VersionMetadata = Join<
    Join<ArchiveEntryId, CommitHash>,
    Join<Indexed<String>, Timestamp>
>

/**
 * Represents a snapshot of the codebase, mapping file paths to their content.
 * Indexed<Join<FilePath, FileContent>>
 */
typealias CodeSnapshot = Indexed<Join<FilePath, FileContent>>

/**
 * Represents a single entry in the DGM archive, combining a code snapshot with its metadata.
 * Join<CodeSnapshot, VersionMetadata>
 */
typealias ArchiveEntry = Join<CodeSnapshot, VersionMetadata>

/**
 * The DGM Archive, a series of archive entries.
 * Indexed<Join<ArchiveEntryId, ArchiveEntry>>
 */
typealias DgmArchive = Indexed<Join<ArchiveEntryId, ArchiveEntry>>

/**
 * Represents a DGM task for proposing and validating an improvement.
 * Join<
 *     Join<TaskId, ArchiveEntryId>,      // TaskId, ParentEntryId for improvement
 *     Join<BenchmarkId, Indexed<String>> // BenchmarkId to run, Optional parameters/config
 * >
 */
typealias DgmTask = Join<
    Join<TaskId, ArchiveEntryId>,
    Join<BenchmarkId, Indexed<String>>
>

/**
 * A proposed solution (e.g., by an LLM) before it undergoes validation.
 * Join<
 *     Join<DgmTask, CodeSnapshot>, // Original Task, Proposed Code Changes (diff or full snapshot)
 *     Join<ProposerId, Indexed<String>> // LLM/Proposer ID, Rationale/Explanation
 * >
 */
typealias ImprovementCandidate = Join<
    Join<DgmTask, CodeSnapshot>,
    Join<ProposerId, Indexed<String>>
>

// Types for ValidationResult
typealias MetricName = String
typealias ScoreValue = Double
typealias BenchmarkScore = Join<MetricName, ScoreValue> // MetricName, ScoreValue
typealias ValidationStatus = String // e.g., "PASSED", "FAILED_TESTS", "TIMEOUT", "ERROR"
typealias LogOutput = String

/**
 * Represents the result of validating an ImprovementCandidate against a benchmark.
 * Join<
 *     Join<ValidationStatus, Indexed<BenchmarkScore>>,
 *     LogOutput // Detailed log output or error message
 * >
 */
typealias ValidationResult = Join<
    Join<ValidationStatus, Indexed<BenchmarkScore>>,
    LogOutput
>

/**
 * Represents an improvement that has been validated and accepted into the archive.
 * Join<
 *     Join<ArchiveEntryId, ImprovementCandidate>, // New ArchiveEntryId, Original Candidate
 *     ValidationResult
 * >
 */
typealias ArchivedImprovement = Join<
    Join<ArchiveEntryId, ImprovementCandidate>,
    ValidationResult
>

// Example usage (compiler check, not for final code)
/*
fun example() {
    val parentId: ArchiveEntryId = "initial_commit"
    val commitHash: CommitHash = "abc123def456"
    val tags: Indexed<String> = Indexed.of("improvement", "refactor")
    val time: Timestamp = 1678886400L

    val versionMeta: VersionMetadata = Join(Join(parentId, commitHash), Join(tags, time))

    val file1: Join<FilePath, FileContent> = Join("src/main/File.kt", "package main...")
    val file2: Join<FilePath, FileContent> = Join("README.md", "# Project Title")
    val codeSnap: CodeSnapshot = Indexed.of(file1, file2)

    val archiveEntry: ArchiveEntry = Join(codeSnap, versionMeta)
    val archiveEntryId: ArchiveEntryId = "commit_v2"
    val dgmArchive: DgmArchive = Indexed.of(Join(archiveEntryId, archiveEntry))

    val taskId: TaskId = "task_001"
    val benchmarkId: BenchmarkId = "benchmark_xyz"
    val taskParams: Indexed<String> = Indexed.of("param1_value")
    val dgmTask: DgmTask = Join(Join(taskId, parentId), Join(benchmarkId, taskParams))

    val proposerId: ProposerId = "gpt-4"
    val rationale: Indexed<String> = Indexed.of("Improved readability", "Reduced complexity")
    val proposedChanges: CodeSnapshot = Indexed.of(Join("src/main/File.kt", "package main... // updated"))
    val candidate: ImprovementCandidate = Join(Join(dgmTask, proposedChanges), Join(proposerId, rationale))

    val status: ValidationStatus = "PASSED"
    val scores: Indexed<BenchmarkScore> = Indexed.of(Join("pass_rate", 1.0), Join("lines_changed", -10.0))
    val log: LogOutput = "All tests passed successfully."
    val validationRes: ValidationResult = Join(Join(status, scores), log)

    val newArchiveId: ArchiveEntryId = "commit_v3_validated"
    val archivedImprovement: ArchivedImprovement = Join(Join(newArchiveId, candidate), validationRes)
}
*/
