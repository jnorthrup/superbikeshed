@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.ljson


/**
 * JSON Bitmap Processor
 * Processes structural bitmaps created by JsonBitmapSimd
 */
object JsonBitmapProcessor {
    
    enum class JsStateEvent {
        Unchanged, ScopeOpen, ScopeClose, ValueDelim
    }
    
    /**
     * Decode structural bits from bitmap
     */
    fun decodeToStructuralBits(bitmap: ULongArray, inputSize: Int): UByteArray {
        val output = UByteArray((inputSize + 3) / 4)
        
        for (i in 0 until inputSize) {
            val ulongIndex = i / 16
            val bitPosition = (i % 16) * 4
            
            // Extract 4-bit value
            val bits = if (ulongIndex < bitmap.size) {
                ((bitmap[ulongIndex] shr bitPosition) and 0xFUL).toInt()
            } else {
                0
            }
            
            // Map to structural event
            val structuralBits = when {
                bits and 1 != 0 -> JsStateEvent.ScopeOpen.ordinal
                bits and 2 != 0 -> JsStateEvent.ScopeClose.ordinal
                bits and 4 != 0 -> JsStateEvent.ValueDelim.ordinal
                else -> JsStateEvent.Unchanged.ordinal
            }
            
            // Pack into output
            val outputIndex = i / 4
            val outputShift = (3 - (i % 4)) * 2
            output[outputIndex] = (output[outputIndex].toInt() or (structuralBits shl outputShift)).toUByte()
        }
        
        return output
    }
}