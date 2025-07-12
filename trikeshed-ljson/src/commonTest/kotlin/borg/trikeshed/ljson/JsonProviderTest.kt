@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson

import borg.trikeshed.cursor.*
import kotlin.test.*
import kotlinx.serialization.*
import kotlinx.coroutines.runBlocking

@Serializable
data class TestData(
    val name: String,
    val age: Int,
    val active: Boolean
)

class JsonProviderTest {
    
    @Test
    fun testBasicParsing() {
        val json = """{"name": "test", "value": 42}"""
        val result = Json.parse(json)
        
        assertNotNull(result.component1())
        assertNull(result.component2())
        
        val element = result.component1() as JsonElement.Obj
        assertEquals(2, element.fields.component1())
    }
    
    @Test
    fun testArrayParsing() {
        val json = """[1, 2, 3, "hello"]"""
        val result = Json.parse(json)
        
        assertNotNull(result.component1())
        val array = result.component1() as JsonElement.Arr
        assertEquals(4, array.elements.component1())
        
        assertEquals(JsonElement.Num(1.0), array.elements.component2()(0))
        assertEquals(JsonElement.Str("hello"), array.elements.component2()(3))
    }
    
    @Test
    fun testStringification() {
        val element = JsonElement.Obj(
            \1 j { \2: Int ->
                when (i) {
                    0 -> "name" j JsonElement.Str("test")
                    1 -> "value" j JsonElement.Num(42.0)
                    else -> "unknown" j JsonElement.Null
                }
            }
        )
        
        val json = Json.stringify(element)
        assertTrue(json.contains("name"))
        assertTrue(json.contains("test"))
        assertTrue(json.contains("42"))
    }
    
    @Test
    fun testSerialization() {
        val testData = TestData("Alice", 30, true)
        val json = testData.encodeJson()
        
        assertTrue(json.contains("Alice"))
        assertTrue(json.contains("30"))
        assertTrue(json.contains("true"))
        
        val decoded = json.decodeJson<TestData>()
        assertNotNull(decoded.component1())
        assertEquals(testData, decoded.component1())
    }
    
    @Test
    fun testBBCursiveParser() {
        val provider = BBCursiveJsonProvider()
        val json = """{"test": true, "number": 123.45}"""
        
        val result = provider.parse(json)
        assertNotNull(result.component1())
        assertNull(result.component2())
        
        val obj = result.component1() as JsonElement.Obj
        assertEquals(2, obj.fields.component1())
    }
    
    @Test
    fun testAutoProxy() {
        val proxy = AutoJsonProxy()
        val json = """{"proxy": "test"}"""
        
        val result = proxy.parse(json)
        assertNotNull(result.component1())
        
        val stringified = proxy.stringify(result.component1()!!)
        assertTrue(stringified.contains("proxy"))
        assertTrue(stringified.contains("test"))
    }
    
    @Test
    fun testJsonCursorIntegration() {
        val jsonArray = JsonElement.Arr(
            \1 j { \2: Int ->
                when (i) {
                    0 -> \1 j { \2: Int ->
                        when (j) {
                            0 -> "name" j JsonElement.Str("Alice")
                            1 -> "age" j JsonElement.Num(25.0)
                            else -> "unknown" j JsonElement.Null
                        }
                    })
                    1 -> \1 j { \2: Int ->
                        when (j) {
                            0 -> "name" j JsonElement.Str("Bob")
                            1 -> "age" j JsonElement.Num(30.0)
                            else -> "unknown" j JsonElement.Null
                        }
                    })
                    2 -> \1 j { \2: Int ->
                        when (j) {
                            0 -> "name" j JsonElement.Str("Charlie")
                            1 -> "age" j JsonElement.Num(35.0)
                            else -> "unknown" j JsonElement.Null
                        }
                    })
                    else -> JsonElement.Null
                }
            }
        )
        
        val cursor = JsonCursor.fromJsonArray(jsonArray)
        assertEquals(3, cursor.component1())
        
        val firstRow = cursor.at(0)
        assertEquals("Alice", firstRow.getString(0))
        assertEquals(25, firstRow.getDouble(1)?.toInt())
        
        // Convert back to JSON
        val backToJson = JsonCursor.toJsonArray(cursor)
        assertEquals(3, backToJson.elements.component1())
    }
    
    @Test
    fun testErrorHandling() {
        val invalidJson = """{"invalid": json syntax}"""
        val result = Json.parse(invalidJson)
        
        assertNull(result.component1())
        assertNotNull(result.component2())
        assertTrue(result.component2()!!.isNotEmpty())
    }
    
    @Test
    fun testNullAndBooleans() {
        val json = """{"nullValue": null, "trueValue": true, "falseValue": false}"""
        val result = Json.parse(json)
        
        assertNotNull(result.component1())
        val obj = result.component1() as JsonElement.Obj
        assertEquals(3, obj.fields.component1())
        
        // Find fields by name
        for (i in 0 until obj.fields.component1()) {
            val field = obj.fields.component2()(i)
            when (field.component1()) {
                "nullValue" -> assertEquals(JsonElement.Null, field.component2())
                "trueValue" -> assertEquals(JsonElement.Bool(true), field.component2())
                "falseValue" -> assertEquals(JsonElement.Bool(false), field.component2())
            }
        }
    }
    
    @Test
    fun testNestedStructures() {
        val json = """
        {
            "user": {
                "name": "test",
                "preferences": {
                    "theme": "dark",
                    "notifications": true
                }
            },
            "items": [1, 2, {"id": 3}]
        }
        """.trimIndent()
        
        val result = Json.parse(json)
        assertNotNull(result.component1())
        
        val obj = result.component1() as JsonElement.Obj
        assertEquals(2, obj.fields.component1())
    }

    @Test
    fun testRemoteFileAttentionReadsTargetedRange() = kotlinx.coroutines.runBlocking {
        // TODO: Mock HttpRangeClient and test RemoteFileAttention
        // val httpClient = ...
        // val attention = RemoteFileAttention(httpClient)
        // val url = "https://example.com/large.json.gz"
        // val start = 1000L
        // val length = 500L
        // val flow = attention.readRange(url, start, length)
        // Assert that flow emits expected Indexed<Byte> chunks
        assertTrue(true) // Placeholder
    }

    @Test
    fun testGzipFileAttentionIntegration() = kotlinx.coroutines.runBlocking {
        // TODO: Mock HttpRangeClient and test GzipFileAttention
        // val httpClient = ...
        // val attention = GzipFileAttention(httpClient)
        // val url = "https://example.com/large.json.gz"
        // val flow = attention.readRange(url, 0, 1024)
        // Assert decompressed output
        assertTrue(true) // Placeholder
    }

    @Test
    fun testCreateBitmapAsTensor() {
        // TODO: Use a sample JSON and test createBitmapAsTensor
        // val jsonString = "{"key": "value"}".encodeToByteArray().toUByteArray()
        // val tensor = createBitmapAsTensor(jsonString)
        // Assert tensor shape and content
        assertTrue(true) // Placeholder
    }

    @Test
    fun testJsonStreamingParseArrayStream() = kotlinx.coroutines.runBlocking {
        // TODO: Test streaming parse of large JSON array
        // val json = "[1,2,3,4,5]"
        // val flow = JsonStreamingImpl().parseArrayStream(json)
        // Assert flow emits correct elements
        assertTrue(true) // Placeholder
    }

    @Test
    fun testJsonStreamingParseObjectStream() = kotlinx.coroutines.runBlocking {
        // TODO: Test streaming parse of large JSON object
        // val json = "{"a":1,"b":2}"
        // val flow = JsonStreamingImpl().parseObjectStream(json)
        // Assert flow emits correct key-value pairs
        assertTrue(true) // Placeholder
    }
}