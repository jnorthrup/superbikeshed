package borg.trikeshed.io

import borg.trikeshed.lib.Usable

typealias PosixOffset = Long

expect class PosixFile(path: String?) : Usable {
    val path: String?
    
    override fun open()
    override fun close()
    fun read64(buf: ByteArray): ULong
    fun write64(buf: ByteArray): ULong
    fun seek(offset: PosixOffset, whence: Int): ULong
    val size: PosixOffset
    fun mmap(len: ULong, prot: Int, flags: Int, offset: PosixOffset): MappedPointer
    
    companion object {
        fun open(path: String?, flags: Int, mode: Int?): PosixFile
    }
}

// Using MappedPointer interface from MappedPointer.kt

expect val SEEK_SET_CONSTANT: Int
expect val O_RDONLY_FLAG: Int 
 