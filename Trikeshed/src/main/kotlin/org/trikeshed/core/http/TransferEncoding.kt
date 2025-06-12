package org.trikeshed.core.http

import org.trikeshed.core.Series
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object TransferEncodingHandler {
    private const val CRLF = "\r\n"
    private const val HEX_RADIX = 16
    
    fun encodeChunked(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        
        // Write chunk size in hex
        val sizeHex = data.size.toString(HEX_RADIX)
        output.write(sizeHex.toByteArray())
        output.write(CRLF.toByteArray())
        
        // Write chunk data
        output.write(data)
        output.write(CRLF.toByteArray())
        
        // Write final chunk
        output.write("0".toByteArray())
        output.write(CRLF.toByteArray())
        output.write(CRLF.toByteArray())
        
        return output.toByteArray()
    }
    
    fun decodeChunked(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        var position = 0
        
        while (position < data.size) {
            // Find chunk size line
            val sizeEnd = data.indexOf(CRLF.toByteArray(), position)
            if (sizeEnd == -1) break
            
            val sizeHex = String(data, position, sizeEnd - position, StandardCharsets.US_ASCII)
            val size = sizeHex.toIntOrNull(HEX_RADIX) ?: break
            
            // Move past size line
            position = sizeEnd + CRLF.length
            
            // If size is 0, we're done
            if (size == 0) break
            
            // Copy chunk data
            output.write(data, position, size)
            position += size + CRLF.length
        }
        
        return output.toByteArray()
    }
    
    fun parseChunks(data: ByteArray): Chunks {
        val chunks = mutableListOf<Chunk>()
        var position = 0
        
        while (position < data.size) {
            // Find chunk size line
            val sizeEnd = data.indexOf(CRLF.toByteArray(), position)
            if (sizeEnd == -1) break
            
            val sizeHex = String(data, position, sizeEnd - position, StandardCharsets.US_ASCII)
            val size = sizeHex.toIntOrNull(HEX_RADIX) ?: break
            
            // Move past size line
            position = sizeEnd + CRLF.length
            
            // If size is 0, we're done
            if (size == 0) break
            
            // Get chunk data
            val chunkData = data.copyOfRange(position, position + size)
            position += size + CRLF.length
            
            // Parse trailer if present
            val trailer = if (position < data.size && data[position] == ';'.code.toByte()) {
                parseTrailer(data, position)
            } else {
                emptyMap()
            }
            
            chunks.add(Triple(size, chunkData, trailer))
        }
        
        return Series.from(chunks)
    }
    
    private fun parseTrailer(data: ByteArray, start: Int): Map<String, String> {
        val trailer = mutableMapOf<String, String>()
        var position = start
        
        while (position < data.size) {
            // Find end of trailer line
            val lineEnd = data.indexOf(CRLF.toByteArray(), position)
            if (lineEnd == -1) break
            
            // Parse trailer line
            val line = String(data, position, lineEnd - position, StandardCharsets.US_ASCII)
            val colonIndex = line.indexOf(':')
            if (colonIndex != -1) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                trailer[key] = value
            }
            
            position = lineEnd + CRLF.length
            
            // Check for end of trailer
            if (position + CRLF.length <= data.size &&
                data.copyOfRange(position, position + CRLF.length).contentEquals(CRLF.toByteArray())) {
                break
            }
        }
        
        return trailer
    }
    
    fun negotiateTransferEncoding(acceptEncodings: TransferEncodings): TransferEncoding {
        return if (acceptEncodings.▶.any { it.value == TransferEncoding.CHUNKED.value }) {
            TransferEncoding.CHUNKED
        } else {
            TransferEncoding.IDENTITY
        }
    }
} 