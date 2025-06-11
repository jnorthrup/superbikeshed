package borg.trikeshed.nio

import kotlinx.cinterop.*
import platform.posix.*
import kotlin.coroutines.CoroutineContext
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.FilePath
import borg.trikeshed.lib.DirectoryPath
import borg.trikeshed.lib.toSeries
import borg.trikeshed.lib.FileOffset
import borg.trikeshed.lib.FileSize
import borg.trikeshed.lib.BufferSize
import borg.trikeshed.lib.FileMode
import borg.trikeshed.lib.Join

@OptIn(ExperimentalForeignApi::class)
actual class PosixFileSystemService actual constructor() : FileSystemService {

    override val key: CoroutineContext.Key<*> get() = FileSystemServiceKey

    actual override suspend fun exists(path: FilePath): Boolean {
        return access(path, F_OK) == 0
    }

    actual override suspend fun readAllBytes(path: FilePath): ByteArray {
        val file = openFile(path, FileOpenOpts(read = true)) // Adjusted FileOpenOpts
        try {
            val size = file.size().value // Assuming FileSize has a value property
            if (size > Int.MAX_VALUE) throw OutOfMemoryError("File $path is too large to read into a ByteArray")
            if (size == 0L) return byteArrayOf()
            val buffer = ByteArray(size.toInt())
            var currentOffset = 0
            var retries = 0
            val maxRetries = 5 // Arbitrary retry limit

            // Loop to ensure all bytes are read, common for partial reads from file descriptors
            while (currentOffset < buffer.size) {
                val bytesToRead = buffer.size - currentOffset
                val readResult = file.read(buffer, ItemCount(currentOffset), BufferSize(bytesToRead))
                val bytesRead = readResult.count // Assuming BufferSize has a count property

                if (bytesRead < 0) { // Error condition
                    throw RuntimeException("Error reading file $path. read returned $bytesRead")
                }
                if (bytesRead == 0) { // EOF or possible issue
                    if (currentOffset < buffer.size && retries < maxRetries) {
                        // Potentially a transient issue or slow file system, retry after a small delay
                        kotlinx.coroutines.delay(5L * (retries + 1)) // Simple backoff
                        retries++
                        continue
                    }
                    // If still not all bytes read after retries, or if EOF truly reached early
                    break
                }
                currentOffset += bytesRead.toInt()
                retries = 0 // Reset retries on successful read
            }

            return if (currentOffset < buffer.size) {
                buffer.copyOf(currentOffset) // Return only what was actually read
            } else {
                buffer
            }
        } finally {
            file.close()
        }
    }

    actual override suspend fun writeAllBytes(path: FilePath, bytes: ByteArray) {
        val file = openFile(path, FileOpenOpts(write = true, create = true, truncate = true)) // Adjusted
        try {
            if (bytes.isEmpty()) {
                // Ensure file is created and truncated if bytes is empty
                // The openFile with truncate=true should handle this.
                // If specific handling for empty write is needed, it can be added here.
                return
            }
            var offset = 0
            while (offset < bytes.size) {
                val written = file.write(bytes, ItemCount(offset), BufferSize(bytes.size - offset))
                if (written.count <= 0) throw RuntimeException("Error writing file $path, wrote 0 bytes or error.") // Assuming BufferSize.count
                offset += written.count.toInt()
            }
        } finally {
            file.close()
        }
    }

    actual override suspend fun readString(path: FilePath): String {
        return readAllBytes(path).decodeToString()
    }

    actual override suspend fun writeString(path: FilePath, content: String) {
        writeAllBytes(path, content.encodeToByteArray())
    }

    actual override suspend fun readAllLines(path: FilePath): Series<String> {
        return readAllBytes(path).decodeToString().lines().toSeries()
    }

    // Keeping the old readLinesSeq for now if it's used internally or by other posix code
    // but it's not part of the FileSystemService interface.
    fun readLinesSeq(path: String, charset: String): Sequence<String> {
        return readString(path).lines().asSequence()
    }

    actual override suspend fun writeLines(path: FilePath, lines: Series<String>) {
        val text = lines.joinToString(separator = "\n") // Assumes Series can be joined
        writeString(path, text)
    }

    // streamLines is significantly different in commonMain, needs careful implementation
    actual override fun streamLines(filePath: FilePath, bufsize: BufferSize): Sequence<Join<FileOffset, ByteArray>> {
        // This is a placeholder implementation. A proper one would involve
        // streaming reads and yielding byte arrays for lines.
        // For now, it reads all lines and then converts them, which is not true streaming.
        return readAllLines(filePath).`▶`.asSequence().mapIndexed { index, line ->
            // This is not correct for FileOffset if it's byte offset.
            // This is just a temporary structure.
            Join(FileOffset(index.toLong()), line.encodeToByteArray())
        }
    }

    actual override suspend fun delete(path: FilePath): Boolean {
        val result = platform.posix.remove(path)
        if (result != 0) {
            if (mustExist || errno != ENOENT) {
                throw PosixException.forErrno("Failed to delete file $path")
            }
            return false
        }
        return true
    }

    override suspend fun mktemp(prefix: String?, suffix: String?, directory: String?): String {
        val template = buildString {
            if (directory != null) {
                append(directory.trimEnd('/'))
                append('/')
            }
            append(prefix ?: "tmp.")
            append("XXXXXX")
            if (suffix != null) {
                append(suffix)
            }
        }
        val templateChars = template.cstr
        // mkstemp modifies the template string in place.
        // We need to ensure the templateChars is a mutable copy if the original template string is needed later.
        // However, cstr returns a new CValuesRef each time which is fine for mkstemp.
        val fd = mkstemp(templateChars)
        if (fd == -1) {
            throw PosixException.forErrno("Failed to create temporary file with template $template")
        }
        close(fd)
        return templateChars.toKString()
    }

    override suspend fun mkdir(path: String, mode: Int?, parents: Boolean): Boolean {
        val posixMode = mode?.toUInt() ?: (S_IRWXU or S_IRGRP or S_IXGRP or S_IROTH or S_IXOTH).toUInt() // 0755
        if (parents) {
            val parts = path.split('/')
            var currentPath = ""
            for (part in parts) {
                if (part.isEmpty() && currentPath.isEmpty()) {
                    currentPath = "/"
                    continue
                }
                if (part.isEmpty()) continue

                currentPath = if (currentPath.endsWith("/")) "$currentPath$part" else "$currentPath/$part"
                if (currentPath == "/") continue

                memScoped {
                    val statBuf = alloc<platform.posix.stat>()
                    if (platform.posix.stat(currentPath, statBuf.ptr) != 0) {
                        if (errno == ENOENT) {
                            if (platform.posix.mkdir(currentPath, posixMode) != 0) {
                                throw PosixException.forErrno("Failed to create directory $currentPath")
                            }
                        } else {
                            throw PosixException.forErrno("Failed to stat directory $currentPath for mkdir -p")
                        }
                    } else if (!S_ISDIR(statBuf.st_mode.toInt())) {
                        throw PosixException(EEXIST, "Path $currentPath exists and is not a directory for mkdir -p")
                    }
                }
            }
            return true
        } else {
            if (platform.posix.mkdir(path, posixMode) != 0) {
                if (errno == EEXIST) return false
                throw PosixException.forErrno("Failed to create directory $path")
            }
            return true
        }
    }

    override suspend fun homedir(): String {
        return getenv("HOME")?.toKString() ?: throw RuntimeException("HOME environment variable not set.")
    }

    override suspend fun cwd(): String {
        memScoped {
            val bufferSize = 1024L
            val buffer = allocArray<ByteVar>(bufferSize)
            if (getcwd(buffer, bufferSize.toULong()) == null) {
                throw PosixException.forErrno("Failed to get current working directory")
            }
            return buffer.toKString()
        }
    }

    override suspend fun openFile(path: String, opts: FileOpenOpts): OpenedFileHandle {
        return PosixOpenedFileHandle(path, opts)
    }

    override fun joinPaths(base: String, vararg parts: String): String {
        val significantParts = mutableListOf<String>()
        if (base.isNotEmpty()) {
            significantParts.add(base)
        }
        parts.forEach { if (it.isNotEmpty()) significantParts.add(it) }

        if (significantParts.isEmpty()) return "."

        var currentPath = significantParts[0]

        for (i in 1 until significantParts.size) {
            val part = significantParts[i]
            if (part.startsWith('/')) {
                currentPath = part
            } else {
                currentPath = if (currentPath.endsWith('/')) {
                    currentPath + part
                } else {
                    "$currentPath/$part"
                }
            }
        }
        var normalizedPath = currentPath.replace(Regex("//+"), "/")
        if (normalizedPath != "/" && normalizedPath.endsWith('/')) {
            normalizedPath = normalizedPath.dropLast(1)
        }
        return normalizedPath.ifEmpty { "." }
    }

    override fun dirname(path: String): String {
        if (path.isEmpty()) return "."
        var tempPath = path
        while (tempPath.length > 1 && tempPath.endsWith('/')) {
            tempPath = tempPath.dropLast(1)
        }
        if (tempPath == "/") return "/"
        val lastSlashIndex = tempPath.lastIndexOf('/')
        return when (lastSlashIndex) {
            -1 -> "."
            0 -> "/"
            else -> tempPath.substring(0, lastSlashIndex).ifEmpty { "/" }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class PosixOpenedFileHandle(
    override val path: String,
    private val opts: FileOpenOpts
) : OpenedFileHandle {

    private var posixFd: Int = -1
    private var _closed = false

    // Pointer to a heap-allocated stat struct, valid for the lifetime of this handle.
    private var managedStatPtr: CPointer<platform.posix.stat>? = null

    init {
        var flags = 0
        if (opts.read && opts.write) flags = flags or O_RDWR
        else if (opts.read) flags = flags or O_RDONLY
        else if (opts.write) flags = flags or O_WRONLY
        else throw IllegalArgumentException("FileOpenOpts must specify read and/or write.")

        if (opts.create) flags = flags or O_CREAT
        if (opts.append) flags = flags or O_APPEND
        if (opts.truncate) flags = flags or O_TRUNC
        if (opts.exclusive) flags = flags or O_EXCL
        if (opts.sync) flags = flags or O_SYNC

        val mode: UInt = opts.createPermissions?.toUInt() ?: (S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH).toUInt()

        posixFd = if (opts.create) {
            platform.posix.open(path, flags, mode)
        } else {
            platform.posix.open(path, flags)
        }

        if (posixFd == -1) {
            throw PosixException.forErrno("Failed to open file $path with opts: $opts")
        }

        // Allocate and initialize managedStatPtr
        try {
            managedStatPtr = nativeHeap.alloc<platform.posix.stat>()
            if (fstat(posixFd, managedStatPtr!!) != 0) {
                nativeHeap.free(managedStatPtr!!)
                managedStatPtr = null // Ensure it's null on fstat failure
                throw PosixException.forErrno("fstat failed during initialization for $path (fd: $posixFd)")
            }
        } catch (e: Throwable) {
            // If allocation or fstat fails, close FD and rethrow
            platform.posix.close(posixFd)
            _closed = true
            posixFd = -1
            throw e // rethrow the original exception (OutOfMemoryError or PosixException)
        }
    }

    override val st: borg.trikeshed.nio.stat
        get() {
            if (_closed) throw IllegalStateException("File handle is closed")
            // managedStatPtr is initialized in init or an exception is thrown.
            // If not null, it points to a valid, heap-allocated stat struct.
            managedStatPtr?.let { return borg.trikeshed.nio.stat(it) }
                ?: throw IllegalStateException("stat structure is not available") // Should not happen if init succeeded
        }

    override val st_: CPointer<platform.posix.stat>?
        get() {
            if (_closed) return null
            return managedStatPtr
        }

    // This detailedStat uses CommonStatWrapper which copies the stat data by value.
    // It's independent of managedStatPtr's direct CPointer after creation.
    private val _detailedStatHolder = lazy {
        if (_closed) throw IllegalStateException("File handle is closed")
        // Use the already fstat'd data from managedStatPtr to avoid another fstat call.
        managedStatPtr?.let { CommonStatWrapper(it.pointed.readValue()) }
            ?: throw IllegalStateException("stat structure is not available for detailedStat")
    }
    val detailedStat: CommonStat get() = _detailedStatHolder.value

    override val size: Long
        get() = st.st_size


    override suspend fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (_closed) throw IllegalStateException("File handle is closed")
        if (offset < 0 || count < 0 || offset + count > buffer.size) {
            throw IndexOutOfBoundsException("Invalid offset or count for read operation")
        }
        if (count == 0) return 0

        return buffer.usePinned { pinned ->
            val result = platform.posix.read(posixFd, pinned.addressOf(offset), count.toULong())
            if (result < 0) {
                throw PosixException.forErrno("Read failed for $path (fd: $posixFd)")
            }
            result.toInt()
        }
    }

    override suspend fun write(buffer: ByteArray, offset: Int, count: Int): Int {
        if (_closed) throw IllegalStateException("File handle is closed")
         if (offset < 0 || count < 0 || offset + count > buffer.size) {
            throw IndexOutOfBoundsException("Invalid offset or count for write operation")
        }
        if (count == 0) return 0

        return buffer.usePinned { pinned ->
            val result = platform.posix.write(posixFd, pinned.addressOf(offset), count.toULong())
            if (result < 0) {
                throw PosixException.forErrno("Write failed for $path (fd: $posixFd)")
            }
            result.toInt()
        }
    }

    override suspend fun seek(offset: Long, whence: FileSeekWhence): Long {
        if (_closed) throw IllegalStateException("File handle is closed")
        val posixWhence = when (whence) {
            FileSeekWhence.SET -> SEEK_SET
            FileSeekWhence.CUR -> SEEK_CUR
            FileSeekWhence.END -> SEEK_END
        }
        val result = lseek(posixFd, offset, posixWhence)
        if (result == -1L) {
            throw PosixException.forErrno("Seek failed for $path (fd: $posixFd)")
        }
        return result
    }

    override suspend fun mmap(
        offset: Long,
        size: Long,
        protection: FileMapProtection,
        flags: FileMapFlags
    ): MappedDataRegion {
        if (_closed) throw IllegalStateException("File handle is closed")
        val prot = when (protection) {
            FileMapProtection.NONE -> PROT_NONE
            FileMapProtection.READ -> PROT_READ
            FileMapProtection.WRITE -> PROT_WRITE or PROT_READ
            FileMapProtection.EXEC -> PROT_EXEC
        }
        val mmapFlags = when (flags) {
            FileMapFlags.SHARED -> MAP_SHARED
            FileMapFlags.PRIVATE -> MAP_PRIVATE
            FileMapFlags.FIXED -> MAP_FIXED
            FileMapFlags.ANONYMOUS -> MAP_ANONYMOUS // Note: MAP_ANONYMOUS typically used with fd = -1
        }

        // If MAP_ANONYMOUS is used, fd should be -1 and offset 0.
        // For file-backed mapping, ensure fd is valid.
        val currentFd = if (flags == FileMapFlags.ANONYMOUS) -1 else posixFd
        val currentOffset = if (flags == FileMapFlags.ANONYMOUS) 0L else offset


        val mappedPtr = platform.posix.mmap(null, size.toULong(), prot, mmapFlags, currentFd, currentOffset)
        if (mappedPtr == MAP_FAILED) {
            throw PosixException.forErrno("mmap failed for $path (fd: $currentFd)")
        }
        return PosixMappedDataRegion(mappedPtr, size)
    }

    override suspend fun close() {
        if (!_closed) {
            val fdToClose = posixFd
            val statPtrToFree = managedStatPtr

            // Mark as closed early to prevent re-entry or use after partial close
            _closed = true
            posixFd = -1
            managedStatPtr = null

            var closeError: PosixException? = null
            if (fdToClose != -1) {
                if (platform.posix.close(fdToClose) != 0) {
                    closeError = PosixException.forErrno("Close failed for $path (fd: $fdToClose)")
                }
            }

            statPtrToFree?.let { nativeHeap.free(it) }

            closeError?.let { throw it } // Throw error from close, if any
        }
    }
}


@OptIn(ExperimentalForeignApi::class)
internal class PosixMappedDataRegion(
    private var ptr: CPointer<ByteVar>?,
    override val size: Long
) : MappedDataRegion {
    private var _closed = false

    init {
        if (ptr == null && size > 0) {
            throw IllegalArgumentException("Memory region pointer cannot be null if size is greater than 0")
        }
    }

    override fun getByte(offset: Long): Byte {
        if (_closed) throw IllegalStateException("Mapped region is closed")
        if (offset < 0 || offset >= size) throw IndexOutOfBoundsException("Offset $offset out of bounds for region size $size")
        return ptr!![offset]
    }

    override fun putByte(offset: Long, value: Byte) {
        if (_closed) throw IllegalStateException("Mapped region is closed")
        if (offset < 0 || offset >= size) throw IndexOutOfBoundsException("Offset $offset out of bounds for region size $size")
        ptr!![offset] = value
    }

    override fun close() {
        if (!_closed && ptr != null) {
            var munmapFailed = false
            if (size > 0) { // Only call munmap if size > 0, as per POSIX recommendations
                if (munmap(ptr, size.toULong()) != 0) {
                    munmapFailed = true
                }
            }
            _closed = true
            ptr = null
            if (munmapFailed) {
                // Log or handle, but don't throw from close typically
                println("Warning: munmap failed - ${strerror(errno)?.toKString()}")
            }
        }
    }
}

class PosixException(val errorNumber: Int, message: String) : RuntimeException(message) {
    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun forErrno(contextMessage: String): PosixException {
            val errorNumber = errno
            val errorString = strerror(errorNumber)?.toKString() ?: "Unknown error"
            return PosixException(errorNumber, "$contextMessage: $errorString (errno $errorNumber)")
        }
    }
}
