package borg.trikeshed.isam

import borg.trikeshed.cursor.RowVec
import borg.trikeshed.lib.*

// Ontological type aliases for encoding operations  
@JvmInline value class ColumnIndex(val value: Int)
@JvmInline value class ColumnData(val value: Any?)
@JvmInline value class BufferPosition(val value: Int)

// Encoder dispatch optimization
typealias EncoderDispatch = DoubleDispatchTable<RecordMeta, ColumnData, ByteArray>

object WireProto{
    
    // Centralized encoder dispatch table
    private val encoderDispatch: EncoderDispatch = seriesOf(
        ((wildcard<RecordMeta>() j wildcard<ColumnData>()) j { meta: RecordMeta, data: ColumnData ->
            meta.encoder(data.value)
        })
    )
    
    // Optimized α transformation for column encoding
    private fun encodeColumn(colMeta: RecordMeta, colData: Any?): ByteArray =
        encoderDispatch.exactDispatch(colMeta, ColumnData(colData))
    
    fun writeToBuffer(
        rowVec:RowVec,
        rowBuf: ByteArray,
        meta: Series<RecordMeta>,
    ): ByteArray {
        val rowData = rowVec.left

        // Use Series α transformation with dispatch optimization
        meta.▶.forEachIndexed { x, colMeta ->
            val colData = rowData[x]
            val pos = colMeta.begin
            
            // Double dispatch encoding with Join preservation  
            val colBytes = encodeColumn(colMeta, colData)
            
            colBytes.copyInto(rowBuf, pos, 0, colBytes.size)

            if (meta[x].type.networkSize == null && pos + colBytes.size < meta[x].end)
                rowBuf[pos + colBytes.size] = 0
        }
        return rowBuf
    }
}