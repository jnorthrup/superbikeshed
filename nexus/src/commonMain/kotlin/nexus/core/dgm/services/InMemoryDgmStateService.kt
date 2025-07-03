package nexus.core.dgm.services

import nexus.core.dgm.*
import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Helper for timestamp - platform-specific implementations would be better in a real scenario
// For commonMain, we define 'expect' and provide a default 'actual' for simplicity here.
// In a full KMP project, 'actual' would be in platform-specific source sets (jvmMain, nativeMain, etc.)
expect object TimestampUtils {
    fun now(): Long
}

// Default/common implementation for TimestampUtils.
// This would typically reside in platform-specific modules (e.g., jvmMain, nativeMain).
// For the purpose of this exercise, it's included here to make the commonMain code runnable/testable
// without setting up full platform-specific modules.
internal actual object TimestampUtils {
    // A simple, non-platform-specific way to get a timestamp-like value.
    // In a real JVM environment: System.currentTimeMillis()
    // In JS: Date.now().toLong()
    // In Native: platform.posix.time(null) * 1000L or similar
    // For common testing or a basic version, this can be a simple counter or fixed value if true time isn't critical.
    // However, to make it somewhat realistic for an "in-memory" service that might run over time:
    private var pseudoTime = 0L // Or initialize with a base to avoid zero if meaningful
    private val pseudoTimeLock = Mutex()

    actual fun now(): Long {
        // This is NOT a real time source, just for making the code compile and run.
        // In a real scenario, use proper platform-specific time.
        // For a quick test, we can just return 0 or an incrementing number.
        // kotlinx-datetime library is the proper KMP way: Clock.System.now().toEpochMilliseconds()
        // For now, let's simulate it simply without adding kotlinx-datetime dependency here.
        return kotlin.random.Random.nextLong(1600000000000L, 1700000000000L) // Random recent-ish timestamp
    }
}


class InMemoryDgmStateService : DgmStateService {

    private val archive: MutableMap<ArchiveEntryId, ArchiveEntry> = mutableMapOf()
    private val mutex = Mutex() // To ensure thread-safe access to the mutable archive

    override suspend fun loadArchive(context: CCEKContext): DgmArchive {
        return mutex.withLock {
            val entries = archive.entries.map { entry -> Join(entry.key, entry.value) }
            Indexed.ofList(entries)
        }
    }

    override suspend fun saveArchiveEntry(entry: ArchivedImprovement, context: CCEKContext): ArchiveEntryId {
        val newEntryId = entry.a.a // ArchivedImprovement -> Join<ArchiveEntryId, ...> -> ArchiveEntryId
        val candidate = entry.a.b // ArchivedImprovement -> Join<..., ImprovementCandidate> -> ImprovementCandidate
        val validationResult = entry.b // ArchivedImprovement -> ValidationResult

        val parentEntryId = candidate.a.a.a.b // ImprovementCandidate -> DgmTask -> parent ArchiveEntryId
        val commitHash = "simulated_hash_for_${newEntryId.take(8)}"
        val tags = Indexed.ofList(listOf("improvement", validationResult.a.a.lowercase())) // status as a tag
        val timestamp = TimestampUtils.now()

        val versionMetadata = VersionMetadata(
            Join(parentEntryId, commitHash), // Join<ParentArchiveEntryId, CommitHash>
            Join(tags, timestamp)            // Join<Indexed<String>, Timestamp>
        )

        val codeSnapshot = candidate.a.b // ImprovementCandidate -> CodeSnapshot

        val archiveEntryToStore = ArchiveEntry(codeSnapshot, versionMetadata)

        mutex.withLock {
            if (archive.containsKey(newEntryId)) {
                println("Warning: Overwriting existing ArchiveEntryId in InMemoryDgmStateService: $newEntryId")
            }
            archive[newEntryId] = archiveEntryToStore
        }
        return newEntryId
    }

    override suspend fun getEntrySnapshot(entryId: ArchiveEntryId, context: CCEKContext): CodeSnapshot? {
        return mutex.withLock {
            archive[entryId]?.a // ArchiveEntry.Join<CodeSnapshot, VersionMetadata> -> CodeSnapshot
        }
    }

    override suspend fun getEntryMetadata(entryId: ArchiveEntryId, context: CCEKContext): VersionMetadata? {
        return mutex.withLock {
            archive[entryId]?.b // ArchiveEntry.Join<CodeSnapshot, VersionMetadata> -> VersionMetadata
        }
    }
}
