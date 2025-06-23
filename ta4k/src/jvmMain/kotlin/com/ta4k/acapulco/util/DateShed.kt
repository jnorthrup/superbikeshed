// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/DateShed.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor // Type alias for Indexed<RowVec>
import borg.trikeshed.cursor.RowVec // Type alias for Series2<Any?, () -> ColumnMeta>
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.meta
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.* // Imports Join, Series, j, α, etc.
import borg.trikeshed.acapulco.ml.featureRange // Assuming ported
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

object DateShed {
    /**
     * rowvec.left[0] must be IoInstant type
     */
    fun treatInstant(rowvec: RowVec): RowVec {
        val instant = rowvec.left[0] as Instant
        val td = instant.atOffset(ZoneOffset.UTC)
        val componentizedCursor = componentize(td)

        // TODO: Port or reimplement 'categories' and 'DummySpec.KeepAll' for Trikeshed's Cursor
        // Placeholder: returning the componentized rowvec directly
        // val combined = combine(bottom60, componentizedCursor) // combine needs Indexed<Indexed<T>>
        // return combined.categories(DummySpec.KeepAll) at 0 // 'categories' needs porting

        // Returning the first row of the componentized cursor for now
        return componentizedCursor at 0
    }

    @JvmStatic
    @JvmName("componentize2")
    fun componentize(rowvec: RowVec): RowVec = (rowvec.left[0] as Instant).atOffset(ZoneOffset.UTC).let {
        componentize(it).first // componentize returns Cursor (Indexed<RowVec>), get first row
    }

    @JvmStatic
    @JvmName("componentize1")
    fun componentize(td: OffsetDateTime): Cursor { // Returns Indexed<RowVec>
        val meta: Indexed<ColumnMeta> = s_[
            ColumnMeta("since017", IOMemento.IoInt),
            ColumnMeta("month", IOMemento.IoInt),
            ColumnMeta("dayOfMonth", IOMemento.IoInt),
            ColumnMeta("dayOfWeek", IOMemento.IoInt),
            ColumnMeta("hour", IOMemento.IoInt),
            ColumnMeta("minute", IOMemento.IoInt)
        ]
        val data: Indexed<Indexed<Any>> = s_[ // Outer Series for rows (only 1 row here)
            s_[ // Inner Series for columns in the row
                td.year - 2017,
                td.month.ordinal,
                td.dayOfMonth,
                td.dayOfWeek.ordinal,
                td.hour,
                td.minute
            ]
        ]
        // TODO: Need a SimpleCursor implementation for Trikeshed or construct Indexed<RowVec> directly
        // Constructing Indexed<RowVec> directly:
        return data.size j { rowIndex:Int ->
            val rowData = data[rowIndex]
            rowData.size j { colIndex:Int ->
                rowData[colIndex] j { meta[colIndex] } // Pair value with metadata lambda
            }
        }
        // return SimpleCursor(meta, data) // Replace with Trikeshed equivalent if SimpleCursor is ported
    }


    /**
     * creates the full range of 60 minutes starting from the first second of 2017
     */
    val bottom60: Cursor by lazy { // Cursor is Indexed<RowVec>
        60 j { y: Int -> // Row index
            s_[ // Column Series (RowVec)
                (y % 9) j { ColumnMeta("since017", IOMemento.IoInt) },
                java.time.Month.values()[y % 12].ordinal j { ColumnMeta("month", IOMemento.IoInt) },
                (y % 31 + 1) j { ColumnMeta("dayOfMonth", IOMemento.IoInt) },
                java.time.DayOfWeek.values()[y % 7].ordinal j { ColumnMeta("dayOfWeek", IOMemento.IoInt) },
                (y % 24) j { ColumnMeta("hour", IOMemento.IoInt) },
                y j { ColumnMeta("minute", IOMemento.IoInt) }
            ]
        }
    }
    val scalarsBottom60: Indexed<ColumnMeta> by lazy { bottom60.meta } // Get meta from the cursor


    val bottom60DoubleRanges: Indexed<Twin<Double>> = run { // Use Indexed<Twin<Double>>
        val b = bottom60
        val numCols = scalarsBottom60.size
        numCols j { x:Int -> // Iterate through columns
            // Extract the column as Indexed<Int>, then convert to Indexed<Double>
            val columnIntIndexed: Indexed<Int> = b α { row -> row.left[x] as Int }
            val columnDoubleIndexed: Indexed<Double> = columnIntIndexed α { it.toDouble() }

            // TODO: featureRange needs to be ported or reimplemented for Indexed<Double>
             featureRange(columnDoubleIndexed) // Assuming featureRange accepts Indexed<Double>
            // Placeholder:
             0.0 j 1.0
        }
    }

    /**
     * rowvec.left[0] must be IoInstant type
     */
    @JvmStatic
    fun normalizeInstant(td: Instant): Indexed<Double> { // Returns Indexed<Double>
        val crono = componentize(td.atOffset(ZoneOffset.UTC))
        // Assuming componentize returns a Cursor (Indexed<RowVec>) with one row
        val row: Indexed<Double> = crono.first.left α { todub(it) } // Get first row, extract left (values), convert to double

        val normies = bottom60DoubleRanges
        val res: Indexed<Double> = normies.size j { x:Int ->
             normies[x].normalize(row[x]) // Assuming normalize accepts (Twin<Double>, Double)
            // Placeholder:
             0.0
        }
        return res
    }

    // bolCache defined above
}
