// =====================================================================
// === TrikeShed/src/jvmMain/kotlin/borg/trikeshed/acapulco/util/DateShed.kt ===
// =====================================================================
package borg.trikeshed.acapulco.util // Adjusted package

import borg.trikeshed.cursor.ColumnMeta
import borg.trikeshed.cursor.Cursor // Type alias for Series<RowVec>
import borg.trikeshed.cursor.RowVec // Type alias for Series2<Any?, () -> ColumnMeta>
import borg.trikeshed.cursor.at
import borg.trikeshed.cursor.get
import borg.trikeshed.cursor.meta
import borg.trikeshed.cursor.SimpleCursor // Needs porting or replacement
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.* // Imports Join, Series, j, α, etc.
import borg.trikeshed.common.collections.s_ // Replaces _v for Series creation
import borg.trikeshed.acapulco.ml.DummySpec // Assuming ported
import borg.trikeshed.acapulco.ml.featureRange // Assuming ported
import borg.trikeshed.acapulco.ml.normalize // Assuming ported
import borg.trikeshed.acapulco.util.todub // Assuming ported or replaced
import java.lang.ref.SoftReference
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.collections.component1

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
        // val combined = combine(bottom60, componentizedCursor) // combine needs Series<Series<T>>
        // return combined.categories(DummySpec.KeepAll) at 0 // 'categories' needs porting

        // Returning the first row of the componentized cursor for now
        return componentizedCursor at 0
    }

    @JvmStatic
    @JvmName("componentize2")
    fun componentize(rowvec: RowVec): RowVec = (rowvec.left[0] as Instant).atOffset(ZoneOffset.UTC).let {
        componentize(it).first // componentize returns Cursor (Series<RowVec>), get first row
    }

    @JvmStatic
    @JvmName("componentize1")
    fun componentize(td: OffsetDateTime): Cursor { // Returns Series<RowVec>
        val meta: Series<ColumnMeta> = s_[
            ColumnMeta("since017", IOMemento.IoInt),
            ColumnMeta("month", IOMemento.IoInt),
            ColumnMeta("dayOfMonth", IOMemento.IoInt),
            ColumnMeta("dayOfWeek", IOMemento.IoInt),
            ColumnMeta("hour", IOMemento.IoInt),
            ColumnMeta("minute", IOMemento.IoInt)
        ]
        val data: Series<Series<Any>> = s_[ // Outer Series for rows (only 1 row here)
            s_[ // Inner Series for columns in the row
                td.year - 2017,
                td.month.ordinal,
                td.dayOfMonth,
                td.dayOfWeek.ordinal,
                td.hour,
                td.minute
            ]
        ]
        // TODO: Need a SimpleCursor implementation for Trikeshed or construct Series<RowVec> directly
        // Constructing Series<RowVec> directly:
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
    val bottom60: Cursor by lazy { // Cursor is Series<RowVec>
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
    val scalarsBottom60: Series<ColumnMeta> by lazy { bottom60.meta } // Get meta from the cursor


    val bottom60DoubleRanges: Series<Twin<Double>> = run { // Use Series<Twin<Double>>
        val b = bottom60
        val numCols = scalarsBottom60.size
        numCols j { x:Int -> // Iterate through columns
            // Extract the column as Series<Int>, then convert to Series<Double>
            val columnIntSeries: Series<Int> = b α { row -> row.left[x] as Int }
            val columnDoubleSeries: Series<Double> = columnIntSeries α { it.toDouble() }

            // TODO: featureRange needs to be ported or reimplemented for Series<Double>
             featureRange(columnDoubleSeries) // Assuming featureRange accepts Series<Double>
            // Placeholder:
             0.0 j 1.0
        }
    }

    /**
     * rowvec.left[0] must be IoInstant type
     */
    @JvmStatic
    fun normalizeInstant(td: Instant): Series<Double> { // Returns Series<Double>
        val crono = componentize(td.atOffset(ZoneOffset.UTC))
        // Assuming componentize returns a Cursor (Series<RowVec>) with one row
        val row: Series<Double> = crono.first.left α { todub(it) } // Get first row, extract left (values), convert to double

        val normies = bottom60DoubleRanges
        val res: Series<Double> = normies.size j { x:Int ->
             normies[x].normalize(row[x]) // Assuming normalize accepts (Twin<Double>, Double)
            // Placeholder:
             0.0
        }
        return res
    }

    // bolCache defined above
}
