package borg.trikeshed.net.http3.qpack

import borg.trikeshed.net.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QpackMinimalTest {

    private val encoder = QpackEncoder()
    private val decoder = QpackDecoder()

    private fun encodeDecodeAndAssert(headers: HttpHeaders, streamId: Long = 0L): HttpHeaders {
        val encoded = encoder.encode(headers, streamId)
        assertNotNull(encoded, "Encoded data should not be null")
        return decoder.decode(encoded, streamId)
    }

    @Test
    fun `encode and decode header with static table exact match`() {
        // :method GET is index 17 in static table
        val headers: HttpHeaders = mapOf(":method" to listOf("GET"))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf(":method" to listOf("GET")), decoded)
    }

    @Test
    fun `encode and decode header with static table name reference and literal value`() {
        // :authority (index 0) with a literal value
        val headers: HttpHeaders = mapOf(":authority" to listOf("example.com"))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf(":authority" to listOf("example.com")), decoded)
    }

    @Test
    fun `encode and decode header with literal name and literal value`() {
        val headers: HttpHeaders = mapOf("custom-header" to listOf("custom-value"))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf("custom-header" to listOf("custom-value")), decoded) // Encoder lowercases name
    }

    @Test
    fun `encode and decode multiple headers with mixed types`() {
        val headers: HttpHeaders = mapOf(
            ":method" to listOf("POST"), // Static exact (index 20)
            "content-type" to listOf("application/json"), // Static exact (index 46)
            "x-custom-static-name" to listOf("literal-value"), // Assuming "x-custom-static-name" is NOT in static table for name
            ":authority" to listOf("test.domain.com") // Static name ref (index 0), literal value
        )
        val decoded = encodeDecodeAndAssert(headers)

        val expected = mapOf(
            ":method" to listOf("POST"),
            "content-type" to listOf("application/json"),
            "x-custom-static-name" to listOf("literal-value"), // Encoder lowercases name
            ":authority" to listOf("test.domain.com")
        )
        assertEquals(expected, decoded)
    }

    @Test
    fun `encode and decode header with empty value using static name reference`() {
        // :authority (index 0) with an empty value
        val headers: HttpHeaders = mapOf(":authority" to listOf(""))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf(":authority" to listOf("")), decoded)
    }

    @Test
    fun `encode and decode header with empty value using literal name`() {
        val headers: HttpHeaders = mapOf("empty-value-header" to listOf(""))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf("empty-value-header" to listOf("")), decoded) // Encoder lowercases name
    }

    @Test
    fun `encode and decode multiple values for the same header name`() {
        // Our QPACK encoder iterates through values and creates separate QPACK entries.
        // The decoder aggregates them into a list.
        val headers: HttpHeaders = mapOf("accept-cookie" to listOf("monster", "biscuit"))
        val decoded = encodeDecodeAndAssert(headers)
        // Assuming "accept-cookie" is not in static table by name
        assertEquals(mapOf("accept-cookie" to listOf("monster", "biscuit")), decoded)
    }


    @Test
    fun `encode and decode headers with large static table index (exact match)`() {
        // Using ":status" to "100" (index 62) as an example for exact match
        // This tests the 0xFF prefix for encoder, and corresponding logic for decoder
        val headers: HttpHeaders = mapOf(":status" to listOf("100"))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf(":status" to listOf("100")), decoded)
    }

    @Test
    fun `encode and decode headers with large static table index (name reference)`() {
        // Using "accept-language" (index 71) as name, literal value
        // This tests the 0x5F prefix for encoder, and corresponding logic for decoder
        val headers: HttpHeaders = mapOf("accept-language" to listOf("en-US,en;q=0.9"))
        val decoded = encodeDecodeAndAssert(headers)
        assertEquals(mapOf("accept-language" to listOf("en-US,en;q=0.9")), decoded)
    }
}
```
