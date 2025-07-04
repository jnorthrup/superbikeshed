package borg.trikeshed.ljson

import borg.trikeshed.cursor.*
import kotlin.test.*
import kotlinx.serialization.*

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
        
        assertNotNull(result.a)
        assertNull(result.b)
        
        val element = result.a as JsonElement.Obj
        assertEquals(2, element.fields.a)
    }
    
    @Test
    fun testArrayParsing() {
        val json = """[1, 2, 3, "hello"]"""
        val result = Json.parse(json)
        
        assertNotNull(result.a)
        val array = result.a as JsonElement.Arr
        assertEquals(4, array.elements.a)
        
        assertEquals(JsonElement.Num(1.0), array.elements.b(0))
        assertEquals(JsonElement.Str("hello"), array.elements.b(3))
    }
    
    @Test
    fun testStringification() {
        val element = JsonElement.Obj(
            2 j { i ->
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
        assertNotNull(decoded.a)
        assertEquals(testData, decoded.a)
    }
    
    @Test
    fun testBBCursiveParser() {
        val provider = BBCursiveJsonProvider()
        val json = """{"test": true, "number": 123.45}"""
        
        val result = provider.parse(json)
        assertNotNull(result.a)
        assertNull(result.b)
        
        val obj = result.a as JsonElement.Obj
        assertEquals(2, obj.fields.a)
    }
    
    @Test
    fun testAutoProxy() {
        val proxy = AutoJsonProxy()
        val json = """{"proxy": "test"}"""
        
        val result = proxy.parse(json)
        assertNotNull(result.a)
        
        val stringified = proxy.stringify(result.a!!)
        assertTrue(stringified.contains("proxy"))
        assertTrue(stringified.contains("test"))
    }
    
    @Test
    fun testJsonCursorIntegration() {
        val jsonArray = JsonElement.Arr(
            3 j { i ->
                when (i) {
                    0 -> JsonElement.Obj(2 j { j ->
                        when (j) {
                            0 -> "name" j JsonElement.Str("Alice")
                            1 -> "age" j JsonElement.Num(25.0)
                            else -> "unknown" j JsonElement.Null
                        }
                    })
                    1 -> JsonElement.Obj(2 j { j ->
                        when (j) {
                            0 -> "name" j JsonElement.Str("Bob")
                            1 -> "age" j JsonElement.Num(30.0)
                            else -> "unknown" j JsonElement.Null
                        }
                    })
                    2 -> JsonElement.Obj(2 j { j ->
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
        assertEquals(3, cursor.a)
        
        val firstRow = cursor.at(0)
        assertEquals("Alice", firstRow.getString(0))
        assertEquals(25, firstRow.getDouble(1)?.toInt())
        
        // Convert back to JSON
        val backToJson = JsonCursor.toJsonArray(cursor)
        assertEquals(3, backToJson.elements.a)
    }
    
    @Test
    fun testErrorHandling() {
        val invalidJson = """{"invalid": json syntax}"""
        val result = Json.parse(invalidJson)
        
        assertNull(result.a)
        assertNotNull(result.b)
        assertTrue(result.b!!.isNotEmpty())
    }
    
    @Test
    fun testNullAndBooleans() {
        val json = """{"nullValue": null, "trueValue": true, "falseValue": false}"""
        val result = Json.parse(json)
        
        assertNotNull(result.a)
        val obj = result.a as JsonElement.Obj
        assertEquals(3, obj.fields.a)
        
        // Find fields by name
        for (i in 0 until obj.fields.a) {
            val field = obj.fields.b(i)
            when (field.a) {
                "nullValue" -> assertEquals(JsonElement.Null, field.b)
                "trueValue" -> assertEquals(JsonElement.Bool(true), field.b)
                "falseValue" -> assertEquals(JsonElement.Bool(false), field.b)
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
        assertNotNull(result.a)
        
        val obj = result.a as JsonElement.Obj
        assertEquals(2, obj.fields.a)
    }
}