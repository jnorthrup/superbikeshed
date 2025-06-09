package borg.trikeshed.nio

import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileMode
import borg.trikeshed.lib.Join // For streamLines return type
import borg.trikeshed.lib.Series

// Key for accessing the FileSystemService in a CoroutineContext
object FileSystemServiceKey : CoroutineContext.Key<FileSystemService>

/**
 * Defines the contract for file system operations within the CCEK framework.
 * This service provides an abstraction over platform-specific file I/O.
 */
interface FileSystemService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = FileSystemServiceKey

    /** Checks if a file or directory exists at the given path. */
    suspend fun exists(path: FilePath): Boolean

    /** Reads all bytes from a file. Throws exception if file not found or not readable. */
    suspend fun readAllBytes(path: FilePath): ByteArray

    /** Reads all lines from a file. Throws exception if file not found or not readable. */
    suspend fun readAllLines(path: FilePath): Series<String>

    /** Reads the entire file content as a single String. */
    suspend fun readString(path: FilePath): String

    /** Writes all bytes to a file, overwriting if it exists, creating if not. */
    suspend fun writeAllBytes(path: FilePath, bytes: ByteArray)

    /** Writes a list of strings as lines to a file, overwriting if it exists, creating if not. */
    suspend fun writeLines(path: FilePath, lines: Series<String>)

    /** Writes a string to a file, overwriting if it exists, creating if not. */
    suspend fun writeString(path: FilePath, content: String)

    /** Opens or creates a file with the given options, returning a handle for further operations. */
    suspend fun openFile(path: FilePath, openOpts: FileOpenOpts): OpenedFileHandle

    /** Creates a temporary file or directory. */
    suspend fun mktemp(isDir: Boolean = false, prefix: FilePath? = null, basePath: DirectoryPath? = null): FilePath

    /** Creates a directory. createIntermediateDirs controls if parent directories should be created. */
    suspend fun mkdir(path: DirectoryPath, createIntermediateDirs: Boolean = false): Boolean

    /** Gets the user's home directory path. */
    val homedir: DirectoryPath

    /** Gets the current working directory path. */
    fun cwd(): DirectoryPath

    /** Deletes a file or an empty directory. Returns true on success. */
    suspend fun delete(path: FilePath): Boolean // Can also be used for empty directories by some platforms

    // It might be useful to have a separate deleteDirectory for non-empty ones if needed.
    // suspend fun deleteDirectory(path: DirectoryPath, recursive: Boolean): Boolean

    /** Streams lines from a file, yielding pairs of (offset, lineBytes). */
    fun streamLines(filePath: FilePath, bufsize: BufferSize = BufferSize(8192)): Sequence<Join<FileOffset, ByteArray>>
}

/**
 * Represents an opened file handle, allowing for various I/O operations.
 * Implements AutoCloseable (or equivalent in KMP) for resource management.
 */
interface OpenedFileHandle : AutoCloseable {
    /**
     * Reads up to `length.bytes` from the file into the `buffer`, starting at `offset.value` in the buffer.
     * Returns the number of bytes read, or -1 if EOF (or BufferSize(0) / throw Exception for more Kotlin-idiomatic EOF).
     * This method implies reading from the current file cursor position.
     */
    suspend fun read(buffer: ByteArray, bufferOffset: ItemCount = ItemCount(0), length: BufferSize = BufferSize(buffer.size - bufferOffset.count)): BufferSize

    /**
     * Writes `length.bytes` from the `buffer` (starting at `offset.value` in buffer) to the file.
     * Returns the number of bytes written.
     * This method implies writing to the current file cursor position.
     */
    suspend fun write(buffer: ByteArray, bufferOffset: ItemCount = ItemCount(0), length: BufferSize = BufferSize(buffer.size - bufferOffset.count)): BufferSize

    /**
     * Sets the file position indicator.
     * @param offset The number of bytes to offset from whence.
     * @param whence The position from where the offset is added.
     * @return The new offset from the beginning of the file.
     */
    suspend fun seek(offset: FileOffset, whence: FileSeekWhence): FileOffset

    /** Returns the current size of the file. */
    suspend fun size(): FileSize

    /**
     * Memory maps a region of the file.
     * @param length The length of the region to map.
     * @param protection Desired memory protection of the mapping.
     * @param flags Specifies the type of the mapped object.
     * @param offset Offset from the beginning of the file where the mapping should start.
     * @return A MappedDataRegion representing the mapped memory.
     */
    suspend fun mmap(
        length: FileSize,
        protection: Set<FileMapProtection> = setOf(FileMapProtection.READ),
        flags: Set<FileMapFlags> = setOf(FileMapFlags.SHARED),
        offset: FileOffset = FileOffset(0L)
    ): MappedDataRegion

    // suspend fun flush() // If manual flushing is needed
    // suspend fun truncate(size: FileSize) // If truncation is needed
}

/**
 * Represents a memory-mapped region of a file.
 * Allows direct byte access to the file's content in memory.
 */
interface MappedDataRegion : AutoCloseable {
    /** Returns the size of the mapped region. */
    fun size(): FileSize

    /** Gets a byte at the given offset within the mapped region. */
    fun getByte(offset: FileOffset): Byte

    /** Puts a byte at the given offset within the mapped region (if writable). */
    fun putByte(offset: FileOffset, value: Byte) // Add check for writability based on protection flags?

    /** Gets a sequence of bytes from the mapped region. */
    fun getBytes(offset: FileOffset, length: ByteCount): ByteArray

    // Potentially add getShort, getInt, getLong, etc., and their put counterparts.
}

/**
 * Options for opening a file, controlling behavior like read/write access,
 * creation, appending, and truncation.
 */
data class FileOpenOpts(
    val read: Boolean = false,
    val write: Boolean = false,
    val append: Boolean = false,
    val create: Boolean = false,      // Create if not exists, open otherwise.
    val createNew: Boolean = false,   // Create only if not exists, fail otherwise.
    val truncate: Boolean = false,    // Truncate existing file to zero length if opened for writing.
    val sync: Boolean = false,        // Synchronous I/O (data and metadata).
    val dsync: Boolean = false,       // Synchronous I/O (data only).
    val mode: FileMode? = null        // File permissions on creation (Posix-like).
) {
    init {
        if (createNew) require(create || write) { "createNew requires create or write flag to be true" }
        if (append) require(write) { "append requires write flag to be true" }
        if (truncate) require(write) { "truncate requires write flag to be true" }
    }
}

/** Specifies the starting point for a seek operation in an OpenedFileHandle. */
enum class FileSeekWhence {
    START,   // Seek from the beginning of the file.
    CURRENT, // Seek from the current file pointer position.
    END      // Seek from the end of the file.
}

/** Specifies memory protection for a memory-mapped region. */
enum class FileMapProtection {
    READ,  // Region can be read.
    WRITE, // Region can be written.
    EXECUTE // Region can be executed.
}

/** Specifies flags for a memory-mapped region. */
enum class FileMapFlags {
    SHARED, // Modifications are shared between processes mapping the same region.
    PRIVATE // Modifications are private to the process (copy-on-write).
}

// Consider adding a FileSystemException class
// class FileSystemException(message: String, val ioErrorCode: Int? = null) : Exception(message)
>>>>>>> origin/jules_wip_8844705664950451013
=======
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileMode
import borg.trikeshed.lib.Join // For streamLines return type

// Key for accessing the FileSystemService in a CoroutineContext
object FileSystemServiceKey : CoroutineContext.Key<FileSystemService>

/**
 * Defines the contract for file system operations within the CCEK framework.
 * This service provides an abstraction over platform-specific file I/O.
 */
interface FileSystemService : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = FileSystemServiceKey

    /** Checks if a file or directory exists at the given path. */
    suspend fun exists(path: FilePath): Boolean

    /** Reads all bytes from a file. Throws exception if file not found or not readable. */
    suspend fun readAllBytes(path: FilePath): ByteArray

    /** Reads all lines from a file. Throws exception if file not found or not readable. */
    suspend fun readAllLines(path: FilePath): List<String>

    /** Reads the entire file content as a single String. */
    suspend fun readString(path: FilePath): String

    /** Writes all bytes to a file, overwriting if it exists, creating if not. */
    suspend fun writeAllBytes(path: FilePath, bytes: ByteArray)

    /** Writes a list of strings as lines to a file, overwriting if it exists, creating if not. */
    suspend fun writeLines(path: FilePath, lines: List<String>)

    /** Writes a string to a file, overwriting if it exists, creating if not. */
    suspend fun writeString(path: FilePath, content: String)

    /** Opens or creates a file with the given options, returning a handle for further operations. */
    suspend fun openFile(path: FilePath, openOpts: FileOpenOpts): OpenedFileHandle

    /** Creates a temporary file or directory. */
    suspend fun mktemp(isDir: Boolean = false, prefix: FilePath? = null, basePath: DirectoryPath? = null): FilePath

    /** Creates a directory. createIntermediateDirs controls if parent directories should be created. */
    suspend fun mkdir(path: DirectoryPath, createIntermediateDirs: Boolean = false): Boolean

    /** Gets the user's home directory path. */
    val homedir: DirectoryPath

    /** Gets the current working directory path. */
    fun cwd(): DirectoryPath

    /** Deletes a file or an empty directory. Returns true on success. */
    suspend fun delete(path: FilePath): Boolean // Can also be used for empty directories by some platforms

    // It might be useful to have a separate deleteDirectory for non-empty ones if needed.
    // suspend fun deleteDirectory(path: DirectoryPath, recursive: Boolean): Boolean

    /** Streams lines from a file, yielding pairs of (offset, lineBytes). */
    fun streamLines(filePath: FilePath, bufsize: BufferSize = BufferSize(8192)): Sequence<Join<FileOffset, ByteArray>>
}

/**
 * Represents an opened file handle, allowing for various I/O operations.
 * Implements AutoCloseable (or equivalent in KMP) for resource management.
 */
interface OpenedFileHandle : AutoCloseable {
    /**
     * Reads up to `length.bytes` from the file into the `buffer`, starting at `offset.value` in the buffer.
     * Returns the number of bytes read, or -1 if EOF (or BufferSize(0) / throw Exception for more Kotlin-idiomatic EOF).
     * This method implies reading from the current file cursor position.
     */
    suspend fun read(buffer: ByteArray, bufferOffset: ItemCount = ItemCount(0), length: BufferSize = BufferSize(buffer.size - bufferOffset.count)): BufferSize

    /**
     * Writes `length.bytes` from the `buffer` (starting at `offset.value` in buffer) to the file.
     * Returns the number of bytes written.
     * This method implies writing to the current file cursor position.
     */
    suspend fun write(buffer: ByteArray, bufferOffset: ItemCount = ItemCount(0), length: BufferSize = BufferSize(buffer.size - bufferOffset.count)): BufferSize

    /**
     * Sets the file position indicator.
     * @param offset The number of bytes to offset from whence.
     * @param whence The position from where the offset is added.
     * @return The new offset from the beginning of the file.
     */
    suspend fun seek(offset: FileOffset, whence: FileSeekWhence): FileOffset

    /** Returns the current size of the file. */
    suspend fun size(): FileSize

    /**
     * Memory maps a region of the file.
     * @param length The length of the region to map.
     * @param protection Desired memory protection of the mapping.
     * @param flags Specifies the type of the mapped object.
     * @param offset Offset from the beginning of the file where the mapping should start.
     * @return A MappedDataRegion representing the mapped memory.
     */
    suspend fun mmap(
        length: FileSize,
        protection: Set<FileMapProtection> = setOf(FileMapProtection.READ),
        flags: Set<FileMapFlags> = setOf(FileMapFlags.SHARED),
        offset: FileOffset = FileOffset(0L)
    ): MappedDataRegion

    // suspend fun flush() // If manual flushing is needed
    // suspend fun truncate(size: FileSize) // If truncation is needed
}

/**
 * Represents a memory-mapped region of a file.
 * Allows direct byte access to the file's content in memory.
 */
interface MappedDataRegion : AutoCloseable {
    /** Returns the size of the mapped region. */
    fun size(): FileSize

    /** Gets a byte at the given offset within the mapped region. */
    fun getByte(offset: FileOffset): Byte

    /** Puts a byte at the given offset within the mapped region (if writable). */
    fun putByte(offset: FileOffset, value: Byte) // Add check for writability based on protection flags?

    /** Gets a sequence of bytes from the mapped region. */
    fun getBytes(offset: FileOffset, length: ByteCount): ByteArray

    // Potentially add getShort, getInt, getLong, etc., and their put counterparts.
}

/**
 * Options for opening a file, controlling behavior like read/write access,
 * creation, appending, and truncation.
 */
data class FileOpenOpts(
    val read: Boolean = false,
    val write: Boolean = false,
    val append: Boolean = false,
    val create: Boolean = false,      // Create if not exists, open otherwise.
    val createNew: Boolean = false,   // Create only if not exists, fail otherwise.
    val truncate: Boolean = false,    // Truncate existing file to zero length if opened for writing.
    val sync: Boolean = false,        // Synchronous I/O (data and metadata).
    val dsync: Boolean = false,       // Synchronous I/O (data only).
    val mode: FileMode? = null        // File permissions on creation (Posix-like).
) {
    init {
        if (createNew) require(create || write) { "createNew requires create or write flag to be true" }
        if (append) require(write) { "append requires write flag to be true" }
        if (truncate) require(write) { "truncate requires write flag to be true" }
    }
}

/** Specifies the starting point for a seek operation in an OpenedFileHandle. */
enum class FileSeekWhence {
    START,   // Seek from the beginning of the file.
    CURRENT, // Seek from the current file pointer position.
    END      // Seek from the end of the file.
}

/** Specifies memory protection for a memory-mapped region. */
enum class FileMapProtection {
    READ,  // Region can be read.
    WRITE, // Region can be written.
    EXECUTE // Region can be executed.
}

/** Specifies flags for a memory-mapped region. */
enum class FileMapFlags {
    SHARED, // Modifications are shared between processes mapping the same region.
    PRIVATE // Modifications are private to the process (copy-on-write).
}

// Consider adding a FileSystemException class
// class FileSystemException(message: String, val ioErrorCode: Int? = null) : Exception(message)
>>>>>>> origin/jules_wip_8844705664950451013
