package borg.trikeshed.io

interface MappedFile {
    val size: Long
    val backingStore: Any?

    fun getByte(offset: Long): Byte
    fun setByte(offset: Long, value: Byte)
    fun asByteArray(): ByteArray
    fun close()
    //todo:  remapping and seeking past 30 bits
}

/**
 * Platform-specific factory for creating MappedFile instances
 */
expect object MappedFileFactory {
    fun map(path: String, size: Long, readOnly: Boolean = false): MappedFile
    fun allocate(size: Long): MappedFile
} 