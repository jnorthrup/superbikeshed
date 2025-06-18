package nexus.core.dgm

import borg.trikeshed.lib.Join
import borg.trikeshed.lib.Series

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
 *     Join<Series<String>, Timestamp>    // Tags, Timestamp (Long)
 * >
 */
typealias VersionMetadata = Join<
    Join<ArchiveEntryId, CommitHash>,
    Join<Series<String>, Timestamp>
>

/**
 * Represents a snapshot of the codebase, mapping file paths to their content.
 * Series<Join<FilePath, FileContent>>
 */
typealias CodeSnapshot = Series<Join<FilePath, FileContent>>

/**
 * Represents a single entry in the DGM archive, combining a code snapshot with its metadata.
 * Join<CodeSnapshot, VersionMetadata>
 */
typealias ArchiveEntry = Join<CodeSnapshot, VersionMetadata>

/**
 * The DGM Archive, a series of archive entries.
 * Series<Join<ArchiveEntryId, ArchiveEntry>>
 */
typealias DgmArchive = Series<Join<ArchiveEntryId, ArchiveEntry>>

/**
 * Represents a DGM task for proposing and validating an improvement.
 * Join<
 *     Join<TaskId, ArchiveEntryId>,      // TaskId, ParentEntryId for improvement
 *     Join<BenchmarkId, Series<String>> // BenchmarkId to run, Optional parameters/config
 * >
 */
typealias DgmTask = Join<
    Join<TaskId, ArchiveEntryId>,
    Join<BenchmarkId, Series<String>>
>

/**
 * A proposed solution (e.g., by an LLM) before it undergoes validation.
 * Join<
 *     Join<DgmTask, CodeSnapshot>, // Original Task, Proposed Code Changes (diff or full snapshot)
 *     Join<ProposerId, Series<String>> // LLM/Proposer ID, Rationale/Explanation
 * >
 */
typealias ImprovementCandidate = Join<
    Join<DgmTask, CodeSnapshot>,
    Join<ProposerId, Series<String>>
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
 *     Join<ValidationStatus, Series<BenchmarkScore>>,
 *     LogOutput // Detailed log output or error message
 * >
 */
typealias ValidationResult = Join<
    Join<ValidationStatus, Series<BenchmarkScore>>,
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
    val tags: Series<String> = Series.of("improvement", "refactor")
    val time: Timestamp = 1678886400L

    val versionMeta: VersionMetadata = Join(Join(parentId, commitHash), Join(tags, time))

    val file1: Join<FilePath, FileContent> = Join("src/main/File.kt", "package main...")
    val file2: Join<FilePath, FileContent> = Join("README.md", "# Project Title")
    val codeSnap: CodeSnapshot = Series.of(file1, file2)

    val archiveEntry: ArchiveEntry = Join(codeSnap, versionMeta)
    val archiveEntryId: ArchiveEntryId = "commit_v2"
    val dgmArchive: DgmArchive = Series.of(Join(archiveEntryId, archiveEntry))

    val taskId: TaskId = "task_001"
    val benchmarkId: BenchmarkId = "benchmark_xyz"
    val taskParams: Series<String> = Series.of("param1_value")
    val dgmTask: DgmTask = Join(Join(taskId, parentId), Join(benchmarkId, taskParams))

    val proposerId: ProposerId = "gpt-4"
    val rationale: Series<String> = Series.of("Improved readability", "Reduced complexity")
    val proposedChanges: CodeSnapshot = Series.of(Join("src/main/File.kt", "package main... // updated"))
    val candidate: ImprovementCandidate = Join(Join(dgmTask, proposedChanges), Join(proposerId, rationale))

    val status: ValidationStatus = "PASSED"
    val scores: Series<BenchmarkScore> = Series.of(Join("pass_rate", 1.0), Join("lines_changed", -10.0))
    val log: LogOutput = "All tests passed successfully."
    val validationRes: ValidationResult = Join(Join(status, scores), log)

    val newArchiveId: ArchiveEntryId = "commit_v3_validated"
    val archivedImprovement: ArchivedImprovement = Join(Join(newArchiveId, candidate), validationRes)
}
*/
