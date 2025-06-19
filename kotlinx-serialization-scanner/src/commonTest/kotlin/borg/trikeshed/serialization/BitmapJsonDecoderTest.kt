package borg.trikeshed.serialization

import kotlinx.serialization.*
import kotlin.test.*

@Serializable
data class Person(val name: String, val age: Int, val isActive: Boolean)

@Serializable
data class Company(val name: String, val employees: List<Person>, val founded: Int)

@Serializable 
data class Address(val street: String, val city: String, val zipCode: String?)

@Serializable
data class Contact(val person: Person, val address: Address)

class BitmapJsonDecoderTest {
    
    @Test
    fun testSimpleObjectDecoding() {
        val json = """{"name":"John","age":30,"isActive":true}"""
        val person = json.decodeBitmapJson<Person>()
        
        assertEquals("John", person.name)
        assertEquals(30, person.age)
        assertTrue(person.isActive)
    }
    
    @Test
    fun testArrayDecoding() {
        val json = """[
            {"name":"Alice","age":25,"isActive":true},
            {"name":"Bob","age":35,"isActive":false}
        ]"""
        
        val people = json.decodeBitmapJson<List<Person>>()
        
        assertEquals(2, people.size)
        assertEquals("Alice", people[0].name)
        assertEquals("Bob", people[1].name)
    }
    
    @Test
    fun testNestedObjectDecoding() {
        val json = """{
            "name": "TechCorp",
            "employees": [
                {"name":"Alice","age":25,"isActive":true},
                {"name":"Bob","age":35,"isActive":false}
            ],
            "founded": 2010
        }"""
        
        val company = json.decodeBitmapJson<Company>()
        
        assertEquals("TechCorp", company.name)
        assertEquals(2010, company.founded)
        assertEquals(2, company.employees.size)
        assertEquals("Alice", company.employees[0].name)
    }
    
    @Test
    fun testNullHandling() {
        val json = """{
            "person": {"name":"John","age":30,"isActive":true},
            "address": {"street":"123 Main St","city":"Boston","zipCode":null}
        }"""
        
        val contact = json.decodeBitmapJson<Contact>()
        
        assertEquals("John", contact.person.name)
        assertEquals("Boston", contact.address.city)
        assertNull(contact.address.zipCode)
    }
    
    @Test
    fun testStringEscaping() {
        val json = """{"name":"John \"The Great\"","age":30,"isActive":true}"""
        val person = json.decodeBitmapJson<Person>()
        
        assertEquals("John \"The Great\"", person.name)
    }
    
    @Test
    fun testEmptyArrayAndObject() {
        val emptyArray = "[]"
        val emptyList = emptyArray.decodeBitmapJson<List<Person>>()
        assertTrue(emptyList.isEmpty())
        
        val emptyObject = """{"name":"EmptyCorp","employees":[],"founded":2020}"""
        val company = emptyObject.decodeBitmapJson<Company>()
        assertEquals("EmptyCorp", company.name)
        assertTrue(company.employees.isEmpty())
    }
    
    @Test
    fun testPrimitivesDecoding() {
        val stringJson = """"Hello World""""
        assertEquals("Hello World", stringJson.decodeBitmapJson<String>())
        
        val intJson = "42"
        assertEquals(42, intJson.decodeBitmapJson<Int>())
        
        val boolJson = "true"
        assertTrue(boolJson.decodeBitmapJson<Boolean>())
        
        val nullJson = "null"
        assertNull(nullJson.decodeBitmapJson<String?>())
    }
    
    @Test
    fun testLargeNumbers() {
        @Serializable
        data class Numbers(val smallInt: Int, val largeInt: Long, val decimal: Double)
        
        val json = """{"smallInt":123,"largeInt":9223372036854775807,"decimal":3.14159}"""
        val numbers = json.decodeBitmapJson<Numbers>()
        
        assertEquals(123, numbers.smallInt)
        assertEquals(9223372036854775807L, numbers.largeInt)
        assertEquals(3.14159, numbers.decimal, 0.00001)
    }
    
    @Test
    fun testWhitespaceHandling() {
        val json = """  {  "name"  :  "John"  ,  "age"  :  30  ,  "isActive"  :  true  }  """
        val person = json.decodeBitmapJson<Person>()
        
        assertEquals("John", person.name)
        assertEquals(30, person.age)
        assertTrue(person.isActive)
    }
}

class BitmapScanEngineTest {
    
    @Test
    fun testStructuralIndicesExtraction() {
        val json = """{"name":"John","age":30}"""
        val indices = BitmapScanEngine.scanWithQuoteHandling(json)
        
        // Should find: { " " : " " , " " : } positions
        assertTrue(indices.size > 0)
        assertEquals('{', json[indices[0]])
    }
    
    @Test
    fun testQuoteStateHandling() {
        val json = """{"key":"value with \"quotes\" inside"}"""
        val indices = BitmapScanEngine.scanWithQuoteHandling(json)
        
        // Should correctly handle escaped quotes inside strings
        assertTrue(indices.size > 0)
    }
    
    @Test
    fun testArrayStructuralIndices() {
        val json = """[1,2,3]"""
        val indices = BitmapScanEngine.scanWithQuoteHandling(json)
        
        assertTrue(indices.size >= 5) // [ , , ]
        assertEquals('[', json[indices[0]])
        assertEquals(']', json[indices[indices.size - 1]])
    }
    
    @Test
    fun testComplexNestedStructure() {
        val json = """{"users":[{"name":"Alice"},{"name":"Bob"}],"count":2}"""
        val indices = BitmapScanEngine.scanWithQuoteHandling(json)
        
        // Verify all structural characters are found
        val structuralChars = indices.play.map { json[it] }
        assertTrue(structuralChars.contains('{'))
        assertTrue(structuralChars.contains('}'))
        assertTrue(structuralChars.contains('['))
        assertTrue(structuralChars.contains(']'))
        assertTrue(structuralChars.contains(':'))
        assertTrue(structuralChars.contains(','))
    }
}

class StreamingBitmapScannerTest {
    
    @Test
    fun testStreamingScanner() {
        val scanner = StreamingBitmapScanner()
        
        scanner.scanChunk("""{"name":""")
        scanner.scanChunk(""""John","age":30}""")
        
        val indices = scanner.getStructuralIndices()
        assertTrue(indices.size > 0)
    }
    
    @Test
    fun testScannerReset() {
        val scanner = StreamingBitmapScanner()
        
        scanner.scanChunk("""{"test":true}""")
        val firstScan = scanner.getStructuralIndices()
        
        scanner.reset()
        scanner.scanChunk("""{"other":false}""")
        val secondScan = scanner.getStructuralIndices()
        
        // After reset, should start fresh
        assertEquals(0, firstScan.size)
        assertTrue(secondScan.size > 0)
    }
    
    @Test
    fun testChunkBoundaryHandling() {
        val scanner = StreamingBitmapScanner()
        
        // Split a string value across chunks
        scanner.scanChunk("""{"message":"Hello""")
        scanner.scanChunk(""" World","complete":true}""")
        
        val indices = scanner.getStructuralIndices()
        assertTrue(indices.size > 0)
    }
}

class BitmapPerformanceTest {
    
    @Test
    fun testLargeJsonPerformance() {
        // Generate large JSON for performance testing
        val largeJson = buildString {
            append("""{"users":[""")
            repeat(1000) { i ->
                if (i > 0) append(",")
                append("""{"id":$i,"name":"User$i","active":${i % 2 == 0}}""")
            }
            append("""]}""")
        }
        
        val startTime = kotlinx.datetime.Clock.System.now()
        val result = largeJson.decodeBitmapJson<Map<String, List<Map<String, Any>>>>()
        val endTime = kotlinx.datetime.Clock.System.now()
        
        val users = result["users"] as List<*>
        assertEquals(1000, users.size)
        
        // Performance should be reasonable (implementation-dependent)
        val durationMs = (endTime - startTime).inWholeMilliseconds
        println("Large JSON decode took: ${durationMs}ms")
    }
    
    @Test
    fun testBitmapVsTraditionalParsing() {
        val json = """{"data":[1,2,3,4,5],"metadata":{"count":5,"type":"numbers"}}"""
        
        // Test bitmap scanning
        val bitmapStart = kotlinx.datetime.Clock.System.now()
        val (bitmap, indices) = scanJsonStructure(json)
        val bitmapEnd = kotlinx.datetime.Clock.System.now()
        
        // Verify bitmap scanning worked
        assertTrue(bitmap.size > 0)
        assertTrue(indices.size > 0)
        
        val bitmapDuration = (bitmapEnd - bitmapStart).inWholeNanoseconds
        println("Bitmap scanning took: ${bitmapDuration}ns")
    }
}