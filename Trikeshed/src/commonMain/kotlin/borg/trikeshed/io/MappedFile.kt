package borg.trikeshed.io

expect class MappedFile {
    constructor(path: String, size: Long, readOnly: Boolean)
    fun close()
    fun open()
    fun isOpen(): Boolean
    fun size(): Long
    fun get(index: Long): Byte
    fun put(index: Long, value: Byte)
}

actual class MappedFile {
    val size: Long
    val backingStore: Any?

    fun getByte(offset: Long): Byte
    fun setByte(offset: Long, value: Byte)
    fun asByteArray(): ByteArray
    fun close()

    companion object {
        fun map(path: String, size: Long, readOnly: Boolean = false): MappedFile
        fun allocate(size: Long): MappedFile
    }
    //todo:  remapping and seeking past 30 bits
    
} 