package borg.trikeshed.cursor

import borg.trikeshed.lib.Series
import borg.trikeshed.cursor.RowVec

class CursorVolume(
    private val series: Series<Series<RowVec>>,
    private val cacheLineSize: Int = 64
) {
    fun reconstitute(useCase: String): Series<RowVec> {
        return when (useCase) {
            "cache_aligned" -> alignToCacheLines()
            "sequential" -> optimizeForSequentialAccess()
            "random" -> optimizeForRandomAccess()
            else -> series.flatten()
        }
    }

    private fun alignToCacheLines(): Series<RowVec> {
        // Align data to cache lines for optimal memory access
        return series.map { s ->
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

    private fun optimizeForSequentialAccess(): Series<RowVec> {
        // Optimize for sequential access patterns
        return series.map { s ->
            s.map { row ->
                // Reorder fields for sequential access
                row.sortedBy { it.begin }
            }
        }.flatten()
    }

    private fun optimizeForRandomAccess(): Series<RowVec> {
        // Optimize for random access patterns
        return series.map { s ->
            s.map { row ->
                // Reorder fields for random access
                row.sortedBy { it.name }
            }
        }.flatten()
    }
} 