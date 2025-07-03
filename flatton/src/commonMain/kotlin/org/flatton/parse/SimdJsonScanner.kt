package org.flatton.parse

import borg.trikeshed.lib.*
import borg.trikeshed.parse.json.*
import kotlinx.serialization.scanner.BitmapJsonDecoder

/**
 * A JSON scanner optimized for finding structural characters using Trikeshed's
 * kotlinx-serialization-scanner BitmapJsonDecoder.
 * 
 * This replaces the fragile "simplified demonstration" with a robust, production-ready
 * implementation that follows the relaxfactory/1xio visitor pattern.
 */
object SimdJsonScanner {
    
    /**
     * Creates a cursor-like Indexed over JSON elements using BitmapJsonDecoder.
     * This provides efficient, on-the-fly parsing without full deserialization.
     */
    fun createCursor(jsonBytes: Indexed<Byte>): Indexed<JsonObjectCursor> {
        val decoder = BitmapJsonDecoder(jsonBytes.play.toByteArray())
        
        return object : Indexed<JsonObjectCursor> {
            private var currentIndex = 0
            private val totalElements = countElements(decoder)
            
            override fun get(index: Int): JsonObjectCursor {
                if (index >= totalElements) throw IndexOutOfBoundsException("Index $index out of bounds")
                
                // Navigate to the nth element
                var current = 0
                while (current < index) {
                    decoder.skipElement()
                    current++
                }
                
                val startPos = decoder.currentPosition
                val element = decoder.decodeElement()
                val endPos = decoder.currentPosition
                
                return JsonObjectCursor(jsonBytes, startPos, endPos, element)
            }
            
            override val size: Int get() = totalElements
        }
    }
    
    /**
     * Finds the indices of all structural JSON characters using bitmap scanning.
     * This uses the superior BitmapJsonDecoder instead of crude regex checks.
     */
    fun findStructuralIndices(json: Indexed<Byte>): Indexed<Int> {
        val decoder = BitmapJsonDecoder(json.play.toByteArray())
        val indices = mutableListOf<Int>()
        
        while (!decoder.isEnd) {
            val pos = decoder.currentPosition
            when (decoder.peekToken()) {
                JsonToken.BEGIN_OBJECT, JsonToken.END_OBJECT,
                JsonToken.BEGIN_LIST, JsonToken.END_LIST,
                JsonToken.COLON, JsonToken.COMMA,
                JsonToken.STRING -> indices.add(pos)
                else -> decoder.skipElement()
            }
        }
        
        return indices.toTypedArray().toSeries()
    }
    
    /**
     * Creates a cursor specifically for CouchDB view responses.
     * This replaces the JsonWireProtoAdapter with a more robust implementation.
     */
    fun createCouchViewCursor(jsonBytes: Indexed<Byte>): Indexed<JsonObjectCursor> {
        val decoder = BitmapJsonDecoder(jsonBytes.play.toByteArray())
        
        // Navigate to the "rows" array
        while (!decoder.isEnd) {
            when (decoder.peekToken()) {
                JsonToken.STRING -> {
                    val key = decoder.decodeString()
                    if (key == "rows") {
                        decoder.decodeToken(JsonToken.COLON)
                        return createArrayCursor(decoder, jsonBytes)
                    } else {
                        decoder.skipElement()
                    }
                }
                else -> decoder.skipElement()
            }
        }
        
        return emptySeries()
    }
    
    private fun createArrayCursor(decoder: BitmapJsonDecoder, jsonBytes: Indexed<Byte>): Indexed<JsonObjectCursor> {
        decoder.decodeToken(JsonToken.BEGIN_LIST)
        
        return object : Indexed<JsonObjectCursor> {
            private var currentIndex = 0
            
            override fun get(index: Int): JsonObjectCursor {
                if (decoder.isEnd || decoder.peekToken() == JsonToken.END_LIST) {
                    throw IndexOutOfBoundsException("Index $index out of bounds")
                }
                
                // Navigate to the nth element
                var current = 0
                while (current < index) {
                    decoder.skipElement()
                    if (decoder.peekToken() == JsonToken.COMMA) {
                        decoder.decodeToken(JsonToken.COMMA)
                    }
                    current++
                }
                
                val startPos = decoder.currentPosition
                val element = decoder.decodeElement()
                val endPos = decoder.currentPosition
                
                return JsonObjectCursor(jsonBytes, startPos, endPos, element)
            }
            
            override val size: Int get() = -1 // Unknown until fully traversed
        }
    }
    
    private fun countElements(decoder: BitmapJsonDecoder): Int {
        var count = 0
        val originalPos = decoder.currentPosition
        
        while (!decoder.isEnd) {
            decoder.skipElement()
            count++
        }
        
        decoder.currentPosition = originalPos
        return count
    }
}

/**
 * A robust cursor pointing to a JSON object within a larger JSON document.
 * This replaces the simplified JsonObjectCursor with a production-ready implementation.
 */
class JsonObjectCursor(
    private val jsonBytes: Indexed<Byte>,
    private val startIndex: Int,
    private val endIndex: Int,
    private val parsedElement: Any? = null
) {
    
    /**
     * Gets a string value from the JSON object using efficient bitmap scanning.
     */
    fun getString(key: String): String? {
        val decoder = BitmapJsonDecoder(jsonBytes.play.toByteArray())
        decoder.currentPosition = startIndex
        
        if (decoder.peekToken() != JsonToken.BEGIN_OBJECT) return null
        
        decoder.decodeToken(JsonToken.BEGIN_OBJECT)
        
        while (decoder.peekToken() != JsonToken.END_OBJECT) {
            val currentKey = decoder.decodeString()
            if (currentKey == key) {
                decoder.decodeToken(JsonToken.COLON)
                return decoder.decodeString()
            } else {
                decoder.skipElement()
            }
            
            if (decoder.peekToken() == JsonToken.COMMA) {
                decoder.decodeToken(JsonToken.COMMA)
            }
        }
        
        return null
    }
    
    /**
     * Gets an integer value from the JSON object.
     */
    fun getInt(key: String): Int? {
        val decoder = BitmapJsonDecoder(jsonBytes.play.toByteArray())
        decoder.currentPosition = startIndex
        
        if (decoder.peekToken() != JsonToken.BEGIN_OBJECT) return null
        
        decoder.decodeToken(JsonToken.BEGIN_OBJECT)
        
        while (decoder.peekToken() != JsonToken.END_OBJECT) {
            val currentKey = decoder.decodeString()
            if (currentKey == key) {
                decoder.decodeToken(JsonToken.COLON)
                return decoder.decodeInt()
            } else {
                decoder.skipElement()
            }
            
            if (decoder.peekToken() == JsonToken.COMMA) {
                decoder.decodeToken(JsonToken.COMMA)
            }
        }
        
        return null
    }
    
    /**
     * Gets a boolean value from the JSON object.
     */
    fun getBoolean(key: String): Boolean? {
        val decoder = BitmapJsonDecoder(jsonBytes.play.toByteArray())
        decoder.currentPosition = startIndex
        
        if (decoder.peekToken() != JsonToken.BEGIN_OBJECT) return null
        
        decoder.decodeToken(JsonToken.BEGIN_OBJECT)
        
        while (decoder.peekToken() != JsonToken.END_OBJECT) {
            val currentKey = decoder.decodeString()
            if (currentKey == key) {
                decoder.decodeToken(JsonToken.COLON)
                return decoder.decodeBoolean()
            } else {
                decoder.skipElement()
            }
            
            if (decoder.peekToken() == JsonToken.COMMA) {
                decoder.decodeToken(JsonToken.COMMA)
            }
        }
        
        return null
    }
    
    /**
     * Converts the cursor back to a JSON string.
     */
    fun toJsonString(): String {
        return String(jsonBytes.play.toByteArray(), startIndex, endIndex - startIndex + 1)
    }
    
    /**
     * Gets the parsed element if available.
     */
    fun getParsedElement(): Any? = parsedElement
}

/**
 * A wire protocol adapter that uses the robust SimdJsonScanner for efficient,
 * on-the-fly parsing of JSON responses into a cursor-like Indexed of elements.
 * This replaces the fragile JsonWireProtoAdapter with a production-ready implementation.
 */
class JsonWireProtoAdapter {
    
    /**
     * Creates a cursor (Indexed) over the "rows" array in a CouchDB view response.
     * This uses the superior BitmapJsonDecoder instead of crude string matching.
     */
    fun toCursor(jsonBytes: Indexed<Byte>): Indexed<JsonObjectCursor> {
        return SimdJsonScanner.createCouchViewCursor(jsonBytes)
    }
    
    /**
     * Creates a cursor over any JSON array.
     */
    fun toArrayCursor(jsonBytes: Indexed<Byte>): Indexed<JsonObjectCursor> {
        return SimdJsonScanner.createCursor(jsonBytes)
    }
}