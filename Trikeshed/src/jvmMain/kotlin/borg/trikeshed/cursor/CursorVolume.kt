package borg.trikeshed.cursor

import borg.trikeshed.lib.Indexed

class CursorVolume(
    private val indexed: Indexed<Indexed<RowVec>>,
    private val cacheLineSize: Int = 64
) {
    fun reconstitute(useCase: String): Indexed<RowVec> {
        return when (useCase) {
            "cache_aligned" -> alignToCacheLines()
            "sequential" -> optimizeForSequentialAccess()
            "random" -> optimizeForRandomAccess()
            else -> indexed.flatten()
        }
    }

    private fun alignToCacheLines(): Indexed<RowVec> {
        // Align data to cache lines for optimal memory access
        return indexed.map { s ->
            s.map { row ->
                // Pad row to cache line boundary if needed
                val padding = (cacheLineSize - (row.size % cacheLineSize)) % cacheLineSize
                if (padding > 0) {
                    row + ByteArray(padding)
                } else {
                    row
                }
            }
        }.flatten()
    }

    private fun optimizeForSequentialAccess(): Indexed<RowVec> {
        // Optimize for sequential access patterns
        return indexed.map { s ->
            s.map { row ->
                // Reorder fields for sequential access
                row.sortedBy { it.begin }
            }
        }.flatten()
    }

    private fun optimizeForRandomAccess(): Indexed<RowVec> {
        // Optimize for random access patterns
        return indexed.map { s ->
            s.map { row ->
                // Reorder fields for random access
                row.sortedBy { it.name }
            }
        }.flatten()
    }
} 