package data

import com.google.trike.series.DataTypes.*
import com.google.trike.series.IOMemento
import com.google.trike.series.RecordMeta

/**
 * Defines the structure and data types for Klines records.
 *
 * This List<RecordMeta> is used for parsing Klines data from CSV files and storing it in ISAM format.
 * It specifies the column names, their corresponding data types, and byte offsets for fixed-width binary representation.
 */
val klinesRecordMeta: List<RecordMeta> = run {
    var currentOffset = 0
    val timestampMeta = RecordMeta.Builder().timestamp("timestamp", IOMemento.Long, begin = currentOffset, end = currentOffset + 8).build()
    currentOffset += 8
    val openMeta = RecordMeta.Builder().col("open", DOUBLE, IOMemento.Double, begin = currentOffset, end = currentOffset + 8).build()
    currentOffset += 8
    val highMeta = RecordMeta.Builder().col("high", DOUBLE, IOMemento.Double, begin = currentOffset, end = currentOffset + 8).build()
    currentOffset += 8
    val lowMeta = RecordMeta.Builder().col("low", DOUBLE, IOMemento.Double, begin = currentOffset, end = currentOffset + 8).build()
    currentOffset += 8
    val closeMeta = RecordMeta.Builder().col("close", DOUBLE, IOMemento.Double, begin = currentOffset, end = currentOffset + 8).build()
    currentOffset += 8
    val volumeMeta = RecordMeta.Builder().col("volume", DOUBLE, IOMemento.Double, begin = currentOffset, end = currentOffset + 8).build()

    listOf(timestampMeta, openMeta, highMeta, lowMeta, closeMeta, volumeMeta)
}
