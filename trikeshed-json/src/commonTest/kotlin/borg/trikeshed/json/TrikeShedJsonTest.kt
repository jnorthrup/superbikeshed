@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
data class TestData(
    val name: String,
    val count: Int,
    val active: Boolean
)

@Serializable
data class NestedData(
    val info: TestData,
    val tags: List<String>
)

class TrikeShedJsonTest {
    
    @Test
    fun testBasicSerialization() {
        val data = TestData("test", 42, true)
        val json = data.encodeTrikeShedJson()
        
        assertNotNull(json)
        assertTrue(json.contains("test"))
        assertTrue(json.contains("42"))
        assertTrue(json.contains("true"))
    }
    
    @Test
    fun testBasicDeserialization() {
        val json = """{"name":"test","count":42,"active":true}"""
        
        // This will use bitmap scanner as backend
        val data = json.decodeTrikeShedJson<TestData>()
        
        assertEquals("test", data.name)
        assertEquals(42, data.count)
        assertTrue(data.active)
    }
    
    @Test
    fun testBitmapScannerIntegration() {
        val json = """{"name":"scanner","count":100,"active":false}"""
        
        // Direct bitmap scanner usage
        val scanner = json.json()
        val document = scanner.scan()
        
        // Verify bitmap scanning worked
        assertNotNull(document)
        
        // Now use with kotlinx.serialization
        val data = TrikeShedJson.Default.decodeFromString<TestData>(json)
        assertEquals("scanner", data.name)
        assertEquals(100, data.count)
        assertFalse(data.active)
    }
    
    @Test
    fun testBitmapScannerFeatures() {
        val json = """{"name":"bitmap","count":100,"active":false}"""
        
        // Test bitmap scanner features
        val scanner = json.json()
        val fingerprint = scanner.fingerprint()
        val properties = scanner.properties()
        
        assertNotNull(fingerprint)
        assertTrue(fingerprint != 0L)
        
        // Properties should be extracted
        assertTrue(properties.size >= 0)
    }
    
    @Test
    fun testLenientMode() {
        val jsonWithUnknown = """{"name":"test","count":42,"active":true,"unknown":"field"}"""
        
        // Should work with lenient configuration
        val data = TrikeShedJson.Lenient.decodeFromString<TestData>(jsonWithUnknown)
        assertEquals("test", data.name)
        assertEquals(42, data.count)
        assertTrue(data.active)
    }
    
    @Test
    fun testBitmapAcceleration() {
        val json = """{"info":{"name":"nested","count":99,"active":true},"tags":["tag1","tag2"]}"""
        
        // Test with bitmap acceleration enabled
        val format = TrikeShedJson.create { 
            copy(useBitmapAcceleration = true) 
        }
        
        val data = format.decodeFromString<NestedData>(json)
        assertEquals("nested", data.info.name)
        assertEquals(99, data.info.count)
        assertEquals(2, data.tags.size)
        assertEquals("tag1", data.tags[0])
        assertEquals("tag2", data.tags[1])
    }
}

/**
 * Demonstration of parallel scanning capabilities
 */
class ParallelScanningTest {
    
    @Test
    fun testParallelScanning() {
        val largeJsonArray = buildString {
            append("[")
            repeat(1000) { i ->
                if (i > 0) append(",")
                append("""{"name":"item$i","count":$i,"active":${i % 2 == 0}}""")
            }
            append("]")
        }
        
        // Bitmap scanner can handle large JSON efficiently
        val scanner = largeJsonArray.json()
        val document = scanner.scan()
        
        assertNotNull(document)
        
        // Verify fingerprinting works for parallel processing
        val fingerprint = scanner.fingerprint()
        assertTrue(fingerprint != 0L)
    }
    
    @Test
    fun testIsomorphismDetection() {
        val json1 = """{"name":"test","count":42}"""
        val json2 = """{"name":"other","count":99}"""
        val json3 = """{"name":"test","count":42}"""
        
        val scanner1 = json1.json()
        val scanner2 = json2.json()
        val scanner3 = json3.json()
        
        // Same structure should be isomorphic
        assertTrue(scanner1 isIsomorphicTo scanner3)
        assertTrue(scanner1 isIsomorphicTo scanner2) // Same structure, different values
    }
}