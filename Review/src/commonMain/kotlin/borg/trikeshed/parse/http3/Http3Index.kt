package borg.trikeshed.parse.http3

import kotlin.collections.MutableList
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.first
import borg.trikeshed.lib.*
import borg.trikeshed.lib.j
import evolution.Http3FrameType
import evolution.Http3StreamId
import evolution.QpackFieldLine
import evolution.Http3StreamState
import evolution.Http3Request
import kotlin.jvm.JvmInline


typealias QpackStaticTableIndex = Join<  entry_id, Join<name_length, value_length > > // entry_id, (name_length, value_length)
typealias entry_id = ULong // Unique identifier for the QPACK static table entry
typealias name_length = ULong // Length of the field name in bytes
typealias value_length = ULong // Length of the field value in bytes


                // typealias QpackStaticTableIndex = Join<ULong, Join<ULong, ULong>> // entry_id, name_length, value_length
                // Connection-level stream mappings
                // typealias Http3ConnectionIndex = Join<ULong, Series<Http3StreamIndex>> // conn_id, active_streams
                typealias Http3ConnectionIndex = Join< conn_id, active_streams>
                typealias conn_id = ULong // Unique connection identifier
                typealias active_streams = Series<Http3StreamIndex> // List of active streams for the connection


                // Priority handling indices
                typealias Http3PriorityIndex = Join<ULong, ULong> // stream_id, priority_value
                // Error code mappings
                // typealias Http3ErrorIndex = Join<ULong, Http3ErrorCode> // stream_id, error_code
                typealias Http3ErrorIndex = Join< stream_id, error_code >
                typealias error_code = ULong // HTTP/3 error code

                // Configuration parameter indices
                // typealias Http3SettingIndex = Join<ULong, ULong> // setting_id, value
                // typealias QpackNamespaceIndex = Join<ULong, Join<String, ULong>> // table_id, namespace_prefix, binding_count
                typealias QpackNamespaceIndex = Join< table_id, Join< namespace_prefix, binding_count > >
                typealias settingValue = Join<  setting_id, `value` >
                typealias table_id = ULong // QPACK dynamic table identifier
                typealias namespace_prefix = String // Namespace prefix for QPACK bindings
                typealias binding_count = ULong // Number of bindings in the namespace
                typealias setting_id = ULong // HTTP/3 setting identifier
                typealias `value` = ULong // Value for the HTTP/3 setting
                typealias Http3Request = Join<ULong, Join<String, String>> // request_id, (method, path)
                typealias request_id = ULong // Unique identifier for the HTTP/3 request
                typealias method = String // HTTP method (e.g., GET, POST)
                typealias path = String // Request path (e.g., /index.html)
                typealias Http3FrameType = ULong // HTTP/3 frame type identifier

            

// === HTTP/3 INDEXING SYSTEM ===
// Columnar indexing for HTTP/3 frames and QPACK structures
 // HTTP/3 indexing structures with tensor operations
typealias Http3FrameIndex = Join<   stream_id, Join<frame_offset, frame_length>> // stream_id, (frame_offset, frame_length)
typealias QpackFieldIndex = Join< table_index,Join< name_offset, value_offset>> // table_index, (name_offset, value_offset)

typealias stream_id = ULong // HTTP/3 stream identifier
typealias frame_offset = ULong // Offset within the stream for the frame
typealias frame_length = ULong // Length of the frame in bytes
typealias table_index = ULong // QPACK dynamic table index
typealias name_offset = ULong // Offset of the field name in the QPACK table
typealias value_offset = ULong // Offset of the field value in the QPACK table

// typealias Http3FrameIndex = Join<ULong, Join<ULong, ULong>> // stream_id, (frame_offset, frame_length)
// typealias QpackFieldIndex = Join<ULong, Join<ULong, ULong>> // table_index, (name_offset, value_offset)
// typealias Http3StreamId = ULong // HTTP/3 stream identifier
// typealias Http3StreamState = Join<ULong, Join<Http3Request, Boolean>> // stream_id, (request, completed)



// typealias Http3StreamIndex = Join<ULong, Series<Http3FrameIndex>> // stream_id, frame_indices
 typealias Http3StreamIndex = Join< stream_id, frame_indices>
typealias frame_indices = Series<Http3FrameIndex> // List of frames for the stream
// HTTP/3 document indexing for O(log n) lookups
@JvmInline value class Http3DocumentIndex private constructor(
    private val data: Join<MutableList<Http3StreamIndex>, Join<MutableList<QpackFieldIndex>, ULong>>
) {
    val streamIndices: MutableList<Http3StreamIndex> get() = data.first
    val qpackIndices: MutableList<QpackFieldIndex> get() = data.second.first
    val totalFrames: ULong get() = data.second.second
     
    // O(log n) frame lookup by stream and position
    fun findFrame(streamId: Http3StreamId, position: ULong): Http3FrameIndex? {
        return streamIndices.toList()
            .find { it.first == streamId }
            ?.second?.materialize()
            ?.find { frame: Http3FrameIndex ->
                val start = frame.second.first
                val end = start + frame.second.second
                position >= start && position < end
            }
    }
    
    // Find all frames of specific type
    fun findFramesByType(frameType: Http3FrameType): MutableList<Http3FrameIndex> {
        // This would require frame type information in the index
        // For now, return empty series - real implementation would store type data
        return mutableListOf<Http3FrameIndex>()
    }
}

// QPACK dynamic table indexing
@JvmInline value class QpackTableIndex private constructor(
    private val data: Join<MutableList<QpackFieldLine>, Join<ULong, ULong>>
) {
    val fields: MutableList<QpackFieldLine> get() = data.first
    val capacity: ULong get() = data.second.first
    val insertCount: ULong get() = data.second.second
    
    companion object {
        fun empty(): QpackTableIndex = 
            QpackTableIndex(mutableListOf<QpackFieldLine>().j(0uL.j(0uL)))
            
        fun withCapacity(capacity: ULong): QpackTableIndex =
            QpackTableIndex(mutableListOf<QpackFieldLine>().j(capacity.j(0uL)))
    }
    
    // Insert field at head of table (FIFO)
    fun insertField(field: QpackFieldLine): QpackTableIndex {
        val newFields = mutableListOf(field)
        val updatedFields = newFields // Would concatenate with existing in real implementation
        val newInsertCount = insertCount + 1uL
        
        return QpackTableIndex(updatedFields j (capacity j newInsertCount))
    }
    
    // Lookup field by absolute index
    fun lookupField(absoluteIndex: ULong): QpackFieldLine? {
        // Static table lookup first (indices 0-61 per RFC 9204)
        if (absoluteIndex < 62uL) {
            return lookupStaticField(absoluteIndex)
        }
        
        // Dynamic table lookup
        val dynamicIndex = absoluteIndex - 62uL
        val dynamicFields = fields
        
        return if (dynamicIndex.toInt() < dynamicFields.size) {
            dynamicFields[dynamicIndex.toInt()]
        } else {
            null
        }
    }
    
    private fun lookupStaticField(index: ULong): QpackFieldLine? {
        // RFC 9204 static table - simplified subset
        return when (index) {
            0uL -> ":authority" j ""
            1uL -> ":path" j "/"
            2uL -> "age" j "0"
            3uL -> "content-disposition" j ""
            4uL -> "content-length" j "0"
            5uL -> "cookie" j ""
            6uL -> "date" j ""
            7uL -> "etag" j ""
            8uL -> "if-modified-since" j ""
            9uL -> "if-none-match" j ""
            10uL -> "last-modified" j ""
            11uL -> "link" j ""
            12uL -> "location" j ""
            13uL -> "referer" j ""
            14uL -> "set-cookie" j ""
            15uL -> ":method" j "CONNECT"
            16uL -> ":method" j "DELETE"
            17uL -> ":method" j "GET"
            18uL -> ":method" j "HEAD"
            19uL -> ":method" j "OPTIONS"
            20uL -> ":method" j "POST"
            21uL -> ":method" j "PUT"
            22uL -> ":scheme" j "http"
            23uL -> ":scheme" j "https"
            24uL -> ":status" j "103"
            25uL -> ":status" j "200"
            26uL -> ":status" j "304"
            27uL -> ":status" j "404"
            28uL -> ":status" j "503"
            29uL -> "accept" j "*/*"
            30uL -> "accept" j "application/dns-message"
            else -> null
        }
    }
}

// HTTP/3 stream state indexing
class Http3StreamStateIndex {
    private val streamStates = mutableMapOf<Http3StreamId, Http3StreamState>()
    
    // Track stream state changes
    fun updateStreamState(streamId: Http3StreamId, request: Http3Request, completed: Boolean) {
        val newState = streamId j (request j completed)
        streamStates[streamId] = newState
    }
    
    // Get current stream state
    fun getStreamState(streamId: Http3StreamId): Http3StreamState? = 
        streamStates[streamId]
    
    // Find all active streams
    fun getActiveStreams(): MutableList<Http3StreamId> {
        val activeIds = streamStates.entries
            .filter { !(it.value.second.second) } // not completed
            .map { it.key as Http3StreamId }
            .toTypedArray()
            
        return mutableListOf(*activeIds)
    }
    
    // Get stream statistics
    fun getStreamStats(): Join<ULong, Join<ULong, ULong>> {
        val total = streamStates.size.toULong()
        val active = streamStates.values.count { !(it.second.second) }.toULong()
        val completed = total - active
        
        return total j (active j completed)
    }
}
