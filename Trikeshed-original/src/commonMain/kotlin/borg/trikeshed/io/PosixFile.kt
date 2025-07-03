package borg.trikeshed.io

import borg.trikeshed.lib.Usable

interface PosixFile : Usable {
    val path: String?
    
    fun open()
    fun close()
}

typealias PosixOffset = Long

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

expect class MappedPointer {
    fun getByte(index: Long): Byte
    fun putByte(index: Long, value: Byte)
    fun unmap()
}

expect val SEEK_SET_CONSTANT: Int
expect val O_RDONLY_FLAG: Int 
 