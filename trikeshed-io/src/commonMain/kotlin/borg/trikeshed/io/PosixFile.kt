@file:OptIn(kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

// import borg.trikeshed.lib.Usable // TODO: Fix Usable dependency

interface PosixFile { // TODO: Removed Usable inheritance
    val path: String?
    
    fun open()
    fun close()
}

typealias PosixOffset = Long

// TODO: Fix expect/actual pattern - commented out for now
/*
expect class PosixFile(path: String?) : PosixFile {
    fun read64(buf: ByteArray): ULong
    fun write64(buf: ByteArray): ULong
    fun seek(offset: PosixOffset, whence: Int): ULong
    val size: PosixOffset
    fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer
    
    companion object {
        fun open(path: String?, flags: Int, mode: Int?): PosixFile
    }
}
*/

// TODO: Fix expect/actual pattern for MappedPointer and constants
/*
expect class MappedPointer {
    fun getByte(index: Long): Byte
    fun putByte(index: Long, value: Byte)
    fun unmap()
}

expect val SEEK_SET_CONSTANT: Int
expect val O_RDONLY_FLAG: Int
*/ 
 