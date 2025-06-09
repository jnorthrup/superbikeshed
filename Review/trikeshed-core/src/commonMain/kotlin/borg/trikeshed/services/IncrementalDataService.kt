package borg.trikeshed.services

import kotlinx.serialization.Serializable
import borg.trikeshed.core.Series
import borg.trikeshed.core.toSeries // Assuming toSeries is available and works for List<T>

// Define the data type that will be part of the incremental updates.
// It must be @Serializable and include a timestamp or version field.
@Serializable
data class YourDataType(val id: String, val value: Double, val timestamp: Long)

// Define the request structure for asking for incremental data.
@Serializable
data class IncrementalDataRequest(
    val dataSource: String, // Identifier for the data source being queried
    val sinceTimestamp: Long? // Nullable, if null, implies fetching all (or from the beginning)
)

package borg.trikeshed.services // Ensure package is at the top

import kotlinx.serialization.Serializable
import borg.trikeshed.core.Series // Already imported by the original file creation
import borg.trikeshed.core.toSeries // My extension, potentially move to TrikeShedCore.kt if more general
import borg.trikeshed.core.toList // My extension
import borg.trikeshed.core.SerializableSeriesData // Moved to top
import borg.trikeshed.core.toSerializable // Moved to top

// Define the data type that will be part of the incremental updates.
// It must be @Serializable and include a timestamp or version field.
@Serializable
data class YourDataType(val id: String, val value: Double, val timestamp: Long)

// Define the request structure for asking for incremental data.
@Serializable
data class IncrementalDataRequest(
    val dataSource: String, // Identifier for the data source being queried
    val sinceTimestamp: Long? // Nullable, if null, implies fetching all (or from the beginning)
)

// Define the response structure that TrikeShed will send back.
@Serializable
data class IncrementalDataResponse(
    val newData: SerializableSeriesData<YourDataType>, // Changed to SerializableSeriesData
    val latestTimestamp: Long          // The timestamp of the latest item in newData, or sinceTimestamp if no new data
)

object IncrementalDataService {

    // Example in-memory data source for demonstration purposes.
    // In a real application, this would interact with actual TrikeShed data structures.
    private val sampleDataSource = mutableListOf<YourDataType>()
    private var currentTimestampCounter = 0L // Internal counter to simulate timestamp generation

    // Synchronized function to add data to the sample source for testing/simulation.
    // This helps in controlling the data state for predictable updates.
    @Synchronized
    fun addSampleData(id: String, value: Double): YourDataType {
        currentTimestampCounter++
        val newItem = YourDataType(id, value, currentTimestampCounter)
        sampleDataSource.add(newItem)
        println("TrikeShed: Added sample data - $newItem")
        return newItem
    }

    // Synchronized function to reset sample data for testing
    @Synchronized
    fun resetSampleData() {
        sampleDataSource.clear()
        currentTimestampCounter = 0L
        println("TrikeShed: Sample data source reset.")
    }

    // Main function to get incremental data based on the request.
    @Synchronized
    fun getIncrementalData(request: IncrementalDataRequest): IncrementalDataResponse {
        println("TrikeShed: Received request: dataSource='${request.dataSource}', sinceTimestamp=${request.sinceTimestamp}")

        val effectiveSinceTimestamp = request.sinceTimestamp ?: 0L

        val dataSinceRequestedTimestampList = sampleDataSource
            .filter { it.timestamp > effectiveSinceTimestamp }

        val latestTimestampInResponse = dataSinceRequestedTimestampList.lastOrNull()?.timestamp ?: effectiveSinceTimestamp

        // Convert the list to Series, then to its serializable form
        val serializableNewData = dataSinceRequestedTimestampList.toSeries().toSerializable()

        println("TrikeShed: Found ${serializableNewData.data.size} new items. Latest timestamp in response: $latestTimestampInResponse")

        return IncrementalDataResponse(
            newData = serializableNewData,
            latestTimestamp = latestTimestampInResponse
        )
    }
}

// Extension function to convert List<T> to Series<T> if not already present
// This is a basic implementation. More sophisticated ones might exist in TrikeShedCore.
internal inline fun <T> List<T>.toSeries(): Series<T> {
    val list = this
    return list.size j { index -> list[index] }
}

// Extension function to convert Series<T> to List<T> for easier processing like lastOrNull()
internal inline fun <T> Series<T>.toList(): List<T> {
    val result = mutableListOf<T>()
    for (i in 0 until this.size) {
        result.add(this[i])
    }
    return result
}
