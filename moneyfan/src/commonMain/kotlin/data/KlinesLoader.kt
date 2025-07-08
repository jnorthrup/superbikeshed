package data

import com.google.trike.series.* // Star import for core Indexed types
import com.google.trike.series.j // Explicit import for 'j' infix if not covered by star, or for clarity
import com.google.trike.series.memseries.* // Star import for MemSeries and related utilities
import com.google.trike.series.storage.* // Star import for IsamDataFile and other storage components

/**
 * Loads Klines data (currently mocked), stores it in ISAM format, and reads it back.
 * This class adheres to CLAUDE.md guidelines by primarily using [Indexed] and [Cursor] types in its public APIs.
 * The structure of Klines data is defined by [klinesRecordMeta].
 */
class KlinesLoader {

    /**
     * Creates a small, fixed [Indexed<RowVec>] of mock kline data.
     * Each [RowVec] in the series conforms to the [klinesRecordMeta] structure.
     * This function is intended for testing and demonstration purposes. In a real-world scenario,
     * this method would parse data from a specified CSV file or another external data source.
     *
     * @param sourcePath The path to the data source (e.g., a CSV file path). Currently unused as data is mocked.
     * @return A [Indexed<RowVec>] containing the mock Klines data.
     */
    fun loadKlinesCsv(sourcePath: String): Indexed<RowVec> {
        // Mock data, structured according to klinesRecordMeta (Timestamp, Open, High, Low, Close, Volume)
        val klinePointsData = listOf(
            listOf(1672531200000L, 100.0, 102.5, 99.5, 101.0, 1000.0), // Record 1
            listOf(1672534800000L, 101.0, 103.0, 100.5, 102.0, 1200.0), // Record 2
            listOf(1672538400000L, 102.0, 104.5, 101.5, 103.5, 1500.0)  // Record 3
        )

        val rowVecs = klinePointsData.map { dataPoint ->
            val joins = klinesRecordMeta.zip(dataPoint).map { (meta, value) ->
                value j { meta } // Using 'j' infix extension function for creating Join objects
            }
            MemSeries.ofJoins(joins) as RowVec // Each list of Joins is cast to a RowVec
        }

        // A SeriesSchema is constructed from klinesRecordMeta to define the structure of the Indexed<RowVec>.
        // This schema is essential for creating a MemSeries of RowVecs.
        val seriesSchema = SeriesSchema(klinesRecordMeta)
        return MemSeries.ofRowVecs(seriesSchema, rowVecs)
    }

    /**
     * Stores Klines data (a [Indexed<RowVec>]) to an ISAM (Indexed Sequential Access Method) file.
     * The schema for the ISAM file is implicitly derived from the metadata of the input [klines] [Indexed].
     * This metadata should originate from [klinesRecordMeta] to ensure consistency.
     * `IsamDataFile.write` handles the creation of both the data file and its associated metadata file (e.g., .meta).
     *
     * @param klines The [Indexed<RowVec>] containing the Klines data to be stored.
     * @param isamDataFilePath The path to the target ISAM data file.
     */
    fun storeKlinesToIsam(klines: Indexed<RowVec>, isamDataFilePath: String) {
        // The schema of the 'klines' Indexed (which should be based on klinesRecordMeta)
        // is used by IsamDataFile.write to structure the ISAM file and create its metadata.
        // The `varChars` map is empty as this Klines data does not use variable-length string types requiring explicit sizing.
        IsamDataFile.write(cursor = klines, datafilename = isamDataFilePath, varChars = emptyMap())
    }

    /**
     * Reads Klines data from an ISAM file, returning a [Cursor] for iteration.
     * The data structure within the ISAM file is expected to conform to [klinesRecordMeta],
     * which is typically defined in the associated .meta file.
     *
     * The caller is responsible for managing the lifecycle of the returned [Cursor], including closing it
     * (e.g., by using a `use` block or calling `close()` explicitly) to free up resources.
     *
     * @param isamDataFilePath The path to the ISAM data file.
     * @param metaFilePath The path to the ISAM metadata file, which defines the schema.
     * @return A [Cursor] for iterating over the Klines data. Each element from the cursor will be a [RowVec].
     * @throws Exception if there is an issue opening or reading the ISAM file (e.g., file not found, corrupted metadata).
     */
    fun readKlinesFromIsam(isamDataFilePath: String, metaFilePath: String): Cursor {
        // IsamDataFile uses the metaFilePath to understand the structure of the data in isamDataFilePath.
        // This structure should align with klinesRecordMeta for consistent data handling.
        try {
            val isamDataFile = IsamDataFile(datafilename = isamDataFilePath, metafileFilename = metaFilePath)
            isamDataFile.open() // Prepares the ISAM file for reading and positions the cursor at the beginning.
            return isamDataFile
        } catch (e: Exception) {
            // It's good practice to log such errors or wrap them in a domain-specific exception.
            // For this context, printing and re-throwing allows the caller to be aware of the failure.
            println("Error opening ISAM file '$isamDataFilePath' with meta file '$metaFilePath': ${e.message}")
            throw e // Rethrowing the original exception or a custom one.
        }
    }
}
