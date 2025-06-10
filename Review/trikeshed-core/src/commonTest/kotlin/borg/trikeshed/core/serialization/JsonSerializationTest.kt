package borg.trikeshed.core.serialization

import borg.trikeshed.core.*
import borg.trikeshed.services.YourDataType
import borg.trikeshed.services.IncrementalDataResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString // Direct import for encodeToString

class JsonSerializationTest {

    private val json = DefaultJson // Using the configured Json instance

    @Test
    fun testTensorSerialization() {
        // Tensor<Int>
        val intTensor = TensorConstruct(intArrayOf(2, 2)) { coords -> coords[0] * 2 + coords[1] }
        val intTensorJson = intTensor.toJsonString()
        assertEquals("""
            {
                "shape": [
                    2,
                    2
                ],
                "data": [
                    0,
                    1,
                    2,
                    3
                ]
            }
            """.replace(Regex("\\s"), ""), intTensorJson.replace(Regex("\\s"), ""))

        // Tensor<String>
        val stringTensor = TensorConstruct(intArrayOf(2)) { coords -> "item${coords[0]}" }
        val stringTensorJson = stringTensor.toJsonString()
        assertEquals("""
            {
                "shape": [
                    2
                ],
                "data": [
                    "item0",
                    "item1"
                ]
            }
            """.replace(Regex("\\s"), ""), stringTensorJson.replace(Regex("\\s"), ""))

        // Empty Tensor
        val emptyTensor = TensorConstruct(intArrayOf(0)) { 0 } // Or intArrayOf(2,0,1)
        val emptyTensorJson = emptyTensor.toJsonString()
        assertEquals("""
            {
                "shape": [
                    0
                ],
                "data": []
            }
            """.replace(Regex("\\s"), ""), emptyTensorJson.replace(Regex("\\s"), ""))

        // Tensor<Byte>
        val byteTensor = TensorConstruct(intArrayOf(3)) { coords -> coords[0].toByte() }
        val byteTensorJson = byteTensor.toJsonString()
        assertEquals("""
            {
                "shape": [
                    3
                ],
                "data": [
                    0,
                    1,
                    2
                ]
            }
            """.replace(Regex("\\s"), ""), byteTensorJson.replace(Regex("\\s"), ""))
    }

    @Test
    fun testSeriesSerialization() {
        // Series<String>
        val stringSeries = List(3) { "s_item$it" }.toSeries() // Helper to create Series
        val stringSeriesJson = stringSeries.toJsonString()
        assertEquals("""
            {
                "data": [
                    "s_item0",
                    "s_item1",
                    "s_item2"
                ]
            }
            """.replace(Regex("\\s"), ""), stringSeriesJson.replace(Regex("\\s"), ""))

        // Empty Series
        val emptySeries = emptyList<Int>().toSeries()
        val emptySeriesJson = emptySeries.toJsonString()
        assertEquals("""
            {
                "data": []
            }
            """.replace(Regex("\\s"), ""), emptySeriesJson.replace(Regex("\\s"), ""))
    }

    @Test
    fun testJoinSerialization() {
        // Join<SerializableTensorData<Int>, SerializableSeriesData<String>>
        // Note: We serialize the *serializable forms* of Tensor/Series when they are part of another structure,
        // or ensure the Join itself is of a type that can be serialized directly (like SerializableJoin).

        val tensorData = TensorConstruct(intArrayOf(1)) { 0 }.toSerializable()
        val seriesData = List(1) { "a" }.toSeries().toSerializable()

        // Using SerializableJoin which is @Serializable
        val join = SerializableJoin(tensorData, seriesData)
        val joinJson = json.encodeToString(join) // Use the Json instance directly for @Serializable types

        assertEquals("""
            {
                "a": {
                    "shape": [
                        1
                    ],
                    "data": [
                        0
                    ]
                },
                "b": {
                    "data": [
                        "a"
                    ]
                }
            }
            """.replace(Regex("\\s"), ""), joinJson.replace(Regex("\\s"), ""))
    }

    @Test
    fun testIncrementalDataResponseSerialization() {
        val items = listOf(
            YourDataType("id1", 1.0, 100L),
            YourDataType("id2", 2.5, 101L)
        )
        val dataSeries = items.toSeries()
        val response = IncrementalDataResponse(newData = dataSeries, latestTimestamp = 101L)

        // Need to use the generic toJsonString for IncrementalDataResponse as it's @Serializable directly
        val responseJson = response.toJsonString()

        // Expected structure for Series<YourDataType> within the response
        // The YourDataType objects will be serialized according to their definition.
        assertEquals("""
            {
                "newData": {
                    "data": [
                        {
                            "id": "id1",
                            "value": 1.0,
                            "timestamp": 100
                        },
                        {
                            "id": "id2",
                            "value": 2.5,
                            "timestamp": 101
                        }
                    ]
                },
                "latestTimestamp": 101
            }
            """.replace(Regex("\\s"), ""), responseJson.replace(Regex("\\s"), ""))
    }

    // Helper to convert List to Series for test setup
    private fun <T> List<T>.toSeries(): Series<T> {
        val list = this
        return list.size j { index -> list[index] }
    }
}
