package borg.trikeshed.ljson

import borg.trikeshed.reactor.currentTimeMillis
import borg.trikeshed.lib.*
import kotlinx.coroutines.flow.*

/**
 * POSIX-style line reader for efficiently reading lines from gzip streams
 * after KZRAN block alignment.
 * 
 * This is a low-level, high-performance line reader that:
 * - Reads aligned block data from HTTP range requests
 * - Processes decompressed gzip stream data line by line
 * - Minimizes allocations and copies
 * - Handles partial lines across block boundaries
 */
object PosixLineReader {
    
    private const val BUFFER_SIZE = 8192
    private const val LF = '\n'.code.toByte()
    private const val CR = '\r'.code.toByte()
    
    /**
     * Read lines from an aligned gzip block stream
     * 
     * @param blockStream Flow of decompressed byte blocks from KZRAN-aligned reads
     * @return Flow of complete lines as byte arrays
     */
    fun readLines(blockStream: Flow<ByteArray>): Flow<ByteArray> = flow {
        val lineBuffer = ByteArrayBuilder()
        
        blockStream.collect { block ->
            var start = 0
            
            for (i in block.indices) {
                if (block[i] == LF) {
                    // Found line ending
                    if (lineBuffer.size > 0) {
                        // Append partial data to existing line
                        lineBuffer.append(block, start, i - start)
                        emit(lineBuffer.build())
                        lineBuffer.clear()
                    } else if (i > start) {
                        // Complete line within this block
                        emit(block.sliceArray(start until i))
                    }
                    
                    start = i + 1
                }
            }
            
            // Save any remaining partial line
            if (start < block.size) {
                lineBuffer.append(block, start, block.size - start)
            }
        }
        
        // Emit final line if no trailing newline
        if (lineBuffer.size > 0) {
            emit(lineBuffer.build())
        }
    }
    
    /**
     * Read lines from indexed byte data (for in-memory processing)
     */
    fun readLines(data: Indexed<Byte>): Flow<Indexed<Byte>> = flow {
        var lineStart = 0
        
        for (i in 0 until data.a) {
            if (data[i] == LF) {
                val lineLength = i - lineStart
                
                if (lineLength > 0) {
                    // Create indexed view of the line
                    val line = lineLength j { j: Int -> data[lineStart + j] }
                    emit(line)
                }
                
                lineStart = i + 1
            }
        }
        
        // Handle last line without newline
        if (lineStart < data.a) {
            val lineLength = data.a - lineStart
            val line = lineLength j { j: Int -> data[lineStart + j] }
            emit(line)
        }
    }
    
    /**
     * Low-level line reader with zero-copy for maximum performance
     * Returns start/end positions instead of copying data
     */
    fun findLines(data: ByteArray): Flow<LinePosition> = flow {
        var lineStart = 0
        
        for (i in data.indices) {
            if (data[i] == LF) {
                if (i > lineStart) {
                    emit(LinePosition(lineStart, i))
                }
                lineStart = i + 1
            }
        }
        
        // Last line
        if (lineStart < data.size) {
            emit(LinePosition(lineStart, data.size))
        }
    }
    
    /**
     * Process lines from KZRAN-aligned gzip blocks with callback
     */
    suspend fun processLinesFromBlocks(
        blocks: Flow<ByteArray>,
        processor: suspend (line: ByteArray) -> Unit
    ) {
        readLines(blocks).collect { line ->
            processor(line)
        }
    }
    
    /**
     * Optimized line counter for large files
     */
    suspend fun countLines(blockStream: Flow<ByteArray>): Long {
        var count = 0L
        
        blockStream.collect { block ->
            for (byte in block) {
                if (byte == LF) count++
            }
        }
        
        return count
    }
    
    /**
     * Read specific line number (0-based) from stream
     */
    suspend fun readLine(blockStream: Flow<ByteArray>, lineNumber: Long): ByteArray? {
        var currentLine = 0L
        var result: ByteArray? = null
        
        readLines(blockStream).collect { line ->
            if (currentLine == lineNumber) {
                result = line
                return@collect
            }
            currentLine++
        }
        
        return result
    }
}

/**
 * Line position in a byte array (zero-copy)
 */
data class LinePosition(
    val start: Int,
    val end: Int  // Exclusive, does not include newline
)

/**
 * Efficient byte array builder for line buffering
 */
private class ByteArrayBuilder {
    private var buffer = ByteArray(256)
    private var position = 0
    
    val size: Int get() = position
    
    fun append(bytes: ByteArray, offset: Int, length: Int) {
        ensureCapacity(position + length)
        System.arraycopy(bytes, offset, buffer, position, length)
        position += length
    }
    
    fun build(): ByteArray {
        return buffer.sliceArray(0 until position)
    }
    
    fun clear() {
        position = 0
    }
    
    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buffer.size) {
            val newSize = maxOf(buffer.size * 2, minCapacity)
            buffer = buffer.copyOf(newSize)
        }
    }
}

/**
 * Extension functions for convenient line reading
 */
fun Flow<ByteArray>.readLines(): Flow<ByteArray> = 
    PosixLineReader.readLines(this)

fun Indexed<Byte>.readLines(): Flow<Indexed<Byte>> = 
    PosixLineReader.readLines(this)

suspend fun Flow<ByteArray>.countLines(): Long = 
    PosixLineReader.countLines(this)

/**
 * Read lines as strings (with encoding)
 */
fun Flow<ByteArray>.readLinesAsStrings(charset: String = "UTF-8"): Flow<String> = flow {
    readLines().collect { lineBytes ->
        emit(lineBytes.decodeToString())
    }
}

/**
 * Process Spansh JSONL data efficiently
 */
fun Flow<ByteArray>.readSpanshSystems(): Flow<GalaxySystem> = flow {
    readLines().collect { lineBytes ->
        try {
            val json = lineBytes.decodeToString()
            JsonLinesParser.parseGalaxySystem(json)?.let { system ->
                emit(system)
            }
        } catch (e: Exception) {
            // Skip malformed lines
        }
    }
}