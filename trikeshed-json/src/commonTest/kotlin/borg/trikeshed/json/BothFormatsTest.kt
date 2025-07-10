@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import borg.trikeshed.lib.*
import borg.trikeshed.core.*
import kotlinx.serialization.*
import kotlin.test.*

@Serializable
data class SimpleTestData(
    val name: String,
    val count: Int,
    val active: Boolean
)

/**
 * Test both bitmap and simple JSON implementations
 */
class BothFormatsTest {
    
    @Test
    fun testBothFormatsBasicSerialization() {
        val data = SimpleTestData("test", 42, true)
        
        // Both should encode to valid JSON
        val bitmapJson = TrikeShedJson.Default.encodeToString(data)
        val simpleJson = TrikeShedJson.Simple.encodeToString(data)
        
        assertNotNull(bitmapJson)
        assertNotNull(simpleJson)
        assertTrue(bitmapJson.contains("test"))
        assertTrue(simpleJson.contains("test"))
        assertTrue(bitmapJson.contains("42"))
        assertTrue(simpleJson.contains("42"))
    }
    
    @Test
    fun testBothFormatsBasicDeserialization() {
        val json = """{"name":"test","count":42,"active":true}"""
        
        // Both should decode the same JSON
        val bitmapData = TrikeShedJson.Default.decodeFromString<SimpleTestData>(json)
        val simpleData = TrikeShedJson.Simple.decodeFromString<SimpleTestData>(json)
        
        // Results should be identical
        assertEquals("test", bitmapData.name)
        assertEquals("test", simpleData.name)
        assertEquals(42, bitmapData.count)
        assertEquals(42, simpleData.count)
        assertTrue(bitmapData.active)
        assertTrue(simpleData.active)
        
        assertEquals(bitmapData, simpleData)
    }
    
    @Test
    fun testBothFormatsLenientMode() {
        val jsonWithUnknown = """{"name":"test","count":42,"active":true,"unknown":"field"}"""
        
        // Both lenient modes should work
        val bitmapData = TrikeShedJson.Lenient.decodeFromString<SimpleTestData>(jsonWithUnknown)
        val simpleData = TrikeShedJson.SimpleLenient.decodeFromString<SimpleTestData>(jsonWithUnknown)
        
        assertEquals(bitmapData, simpleData)
        assertEquals("test", bitmapData.name)
        assertEquals("test", simpleData.name)
    }
    
    @Test
    fun testDirectScannerComparison() {
        val json = """{"name":"scanner","count":100,"active":false}"""
        
        // Test direct scanner usage
        val bitmapScanner = json.json()
        val simpleScanner = json.simpleJson()
        
        // Both should extract properties
        val bitmapProps = bitmapScanner.properties()
        val simpleProps = simpleScanner.properties()
        
        assertTrue(bitmapProps.size > 0)
        assertTrue(simpleProps.size > 0)
        
        // Both should find the same property
        val bitmapName = bitmapScanner.query("name")
        val simpleName = simpleScanner.query("name")
        
        assertNotNull(bitmapName)
        assertNotNull(simpleName)
        assertEquals("scanner", bitmapName.b)
        assertEquals("scanner", simpleName.b)
    }
    
    @Test
    fun testPerformanceCharacteristics() {
        val json = """{"name":"performance","count":999,"active":true}"""
        
        // Simple scanner should work without bitmap overhead
        val simpleScanner = json.simpleJson()
        val simpleDoc = simpleScanner.scan()
        val simpleProps = simpleDoc.a
        
        assertTrue(simpleProps.size > 0)
        
        // Bitmap scanner should work with bitmap features
        val bitmapScanner = json.json()
        val bitmapDoc = bitmapScanner.scan()
        val bitmapFingerprint = bitmapScanner.fingerprint()
        
        assertNotNull(bitmapDoc)
        assertTrue(bitmapFingerprint != 0L)
    }
    
    @Test
    fun testIsomorphismDetection() {
        val json1 = """{"name":"test","count":42}"""
        val json2 = """{"name":"other","count":99}"""
        val json3 = """{"name":"test","count":42}"""
        
        // Test bitmap isomorphism
        val bitmapScanner1 = json1.json()
        val bitmapScanner2 = json2.json()
        val bitmapScanner3 = json3.json()
        
        assertTrue(bitmapScanner1 isIsomorphicTo bitmapScanner2) // Same structure
        assertTrue(bitmapScanner1 isIsomorphicTo bitmapScanner3) // Same structure
        
        // Test simple isomorphism
        val simpleScanner1 = json1.simpleJson()
        val simpleScanner2 = json2.simpleJson()
        val simpleScanner3 = json3.simpleJson()
        
        assertTrue(simpleScanner1 isIsomorphicTo simpleScanner2) // Same structure
        assertTrue(simpleScanner1 isIsomorphicTo simpleScanner3) // Same structure
    }
    
    @Test
    fun testCustomConfiguration() {
        val data = SimpleTestData("custom", 123, false)
        
        // Test custom bitmap configuration
        val bitmapFormat = TrikeShedJson.create {
            copy(useBitmapAcceleration = true, isLenient = true)
        }
        val bitmapJson = bitmapFormat.encodeToString(data)
        val bitmapDecoded = bitmapFormat.decodeFromString<SimpleTestData>(bitmapJson)
        assertEquals(data, bitmapDecoded)
        
        // Test custom simple configuration
        val simpleFormat = TrikeShedJson.createSimple {
            copy(isLenient = true, ignoreUnknownKeys = true)
        }
        val simpleJson = simpleFormat.encodeToString(data)
        val simpleDecoded = simpleFormat.decodeFromString<SimpleTestData>(simpleJson)
        assertEquals(data, simpleDecoded)
    }
    
    @Test
    fun testExtensionFunctions() {
        val data = SimpleTestData("extension", 456, true)
        
        // Test bitmap extensions
        val bitmapJson = data.encodeTrikeShedJson()
        val bitmapDecoded = bitmapJson.decodeTrikeShedJson<SimpleTestData>()
        assertEquals(data, bitmapDecoded)
        
        // Test simple extensions
        val simpleJson = data.encodeSimpleTrikeShedJson()
        val simpleDecoded = simpleJson.decodeSimpleTrikeShedJson<SimpleTestData>()
        assertEquals(data, simpleDecoded)
    }
}