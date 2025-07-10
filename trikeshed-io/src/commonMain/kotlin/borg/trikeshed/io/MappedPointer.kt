@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

interface MappedPointer {
    val size: Long
    
    fun getByte(offset: Long): Byte
    fun putByte(offset: Long, value: Byte)
    fun close()
} 