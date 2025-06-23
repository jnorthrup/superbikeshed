@file:OptIn(ExperimentalForeignApi::class)

package borg.trikeshed.isam

import borg.trikeshed.io.Usable
import borg.trikeshed.cursor.Cursor
import borg.trikeshed.cursor.RowVec
import borg.trikeshed.cursor.meta
import borg.trikeshed.isam.meta.IOMemento
import borg.trikeshed.lib.*
import kotlinx.cinterop.*
import platform.posix.*
import platform.posix.MAP_PRIVATE
import platform.posix.O_RDONLY
import platform.posix.PROT_READ
import platform.posix.close
import platform.posix.mmap
import platform.posix.munmap
import platform.posix.open
import platform.posix.stat
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

actual class IsamDataFile actual constructor(
    datafileFilename: String,
    metafileFilename: String,
    metafile: IsamMetaFileReader,
) : Usable, Cursor {

    actual val datafileFilename: String = datafileFilename
    actual val metafile: IsamMetaFileReader = metafile

    val recordlen: Int by lazy {
        this.metafile.recordlen.also {
            require(it > 0) { "recordlen must be > 0" }
        }
    }

    val constraints: Indexed<RecordMeta> by lazy { metafile.constraints }
    private lateinit var data: COpaquePointer
    var fileSize: Long = -1

    private var first = true
    actual override fun open() {
        if (!first) return
        memScoped {
            val fd = open(datafileFilename, O_RDONLY)
            val stat = alloc<stat>()
            fstat(fd, stat.ptr)
            fileSize = stat.st_size

            require(fileSize % recordlen == 0L) { "fileSize must be a multiple of recordlen" }

            data = mmap(null, fileSize.toULong(), PROT_READ, MAP_PRIVATE, fd, 0)!!
            close(fd)

            // Report on record alignment
            val alignment = fileSize % recordlen
            if (alignment != 0L) {
                println("WARN: file $datafileFilename is not aligned to recordlen $recordlen")
            } else {
                println("DEBUG: file $datafileFilename is aligned to recordlen $recordlen")
            }

            println("DEBUG: each record is ${recordlen.toLong().humanReadableByteCountIEC} bytes long")

            // Field statistics
            val fieldCounts = mutableMapOf<IOMemento, Int>()
            val fieldOccupancy = mutableMapOf<IOMemento, Int>()
            constraints.forEach { constraint ->
                val count = fieldCounts.getOrPut(constraint.type) { 0 }
                fieldCounts[constraint.type] = count + 1
                val occupancy = fieldOccupancy.getOrPut(constraint.type) { 0 }
                fieldOccupancy[constraint.type] = occupancy + constraint.end - constraint.begin
            }
            val recordCount = fileSize / recordlen
            println("DEBUG: file $datafileFilename has $recordCount records in ${fileSize.humanReadableByteCountIEC}")
            fieldCounts.forEach { (type, count) ->
                val occupancy = fieldOccupancy[type]!!
                println("DEBUG: file $datafileFilename has $count fields of type $type occupying $occupancy bytes (${occupancy * 100 / recordlen}%) of each record (${(occupancy * recordCount).humanReadableByteCountSI} in the file)")
            }
        }
    }

    actual override val a: Int
        get() {
            open().let {
                return (fileSize / recordlen).toInt()
            }
        }

    actual override val b: (Int) -> RowVec = { row ->
        memScoped {
            val d2 = data.toLong() + (row * recordlen)

            constraints.size j { col: Int ->
                constraints[col].let { recordMeta ->
                    val d4 = d2 + recordMeta.begin
                    val d5: COpaquePointer = d4.toCPointer()!!
                    val d6: ByteArray = d5.readBytes(recordMeta.end - recordMeta.begin)
                    recordMeta.decoder(d6)!! j { recordMeta }
                }
            }
        }
    }

    override fun toString(): String =
        "IsamDataFile(metafile=$metafile, recordlen=$recordlen, constraints=$constraints, datafileFilename='$datafileFilename', fileSize=$fileSize)"

    actual override fun close() {
        memScoped {
            munmap(data, fileSize.toULong())
        }
    }

    actual companion object {
        actual fun write(cursor: Cursor, datafilename: String, varChars: Map<String, Int>) {
            val metafilename = "$datafilename.meta"
            val meta0 = IsamMetaFileReader.write(metafilename, cursor.meta, varChars)

            // Use modern async I/O with IO_URING
            val ioConfig = AsyncIOConfig(
                useUring = true,
                useScatterGather = true,
                useUnbuffered = true,
                batchSize = 32,
                queueDepth = 64
            )

            val asyncWriter = AsyncIOWriter(ioConfig)
            asyncWriter.writeIsamData(cursor, datafilename, meta0)
        }

        actual fun append(
            msf: Iterable<RowVec>,
            datafilename: String,
            varChars: Map<String, Int>,
            transform: ((RowVec) -> RowVec)?,
        ): Unit {
            // Use async append with IO_URING
            val ioConfig = AsyncIOConfig(
                useUring = true,
                useScatterGather = false,
                useUnbuffered = true,
                batchSize = 16,
                queueDepth = 32
            )

            val asyncWriter = AsyncIOWriter(ioConfig)
            asyncWriter.appendIsamData(msf, datafilename, varChars, transform)
        }
    }
}

// === ASYNC I/O CONFIGURATION DSL ===

/**
 * DSL for configuring async I/O options
 */
data class AsyncIOConfig(
    val useUring: Boolean = true,
    val useScatterGather: Boolean = false,
    val useUnbuffered: Boolean = true,
    val batchSize: Int = 32,
    val queueDepth: Int = 64,
    val useMmap: Boolean = true,
    val useKqueue: Boolean = false,
    val useEpoll: Boolean = false,
    val bufferSize: Int = 4096,
    val alignment: Int = 512,
    val timeout: Long = 5000L,
    val retryCount: Int = 3
) {
    companion object {
        fun uring(): AsyncIOConfig = AsyncIOConfig(
            useUring = true,
            useScatterGather = true,
            useUnbuffered = true
        )

        fun kqueue(): AsyncIOConfig = AsyncIOConfig(
            useUring = false,
            useKqueue = true,
            useScatterGather = false
        )

        fun epoll(): AsyncIOConfig = AsyncIOConfig(
            useUring = false,
            useEpoll = true,
            useScatterGather = false
        )

        fun buffered(): AsyncIOConfig = AsyncIOConfig(
            useUring = false,
            useUnbuffered = false,
            useMmap = false
        )

        fun mmap(): AsyncIOConfig = AsyncIOConfig(
            useUring = false,
            useMmap = true,
            useUnbuffered = true
        )
    }
}

/**
 * DSL builder for async I/O configuration
 */
class AsyncIOConfigDSL {
    private var useUring: Boolean = true
    private var useScatterGather: Boolean = false
    private var useUnbuffered: Boolean = true
    private var batchSize: Int = 32
    private var queueDepth: Int = 64
    private var useMmap: Boolean = true
    private var useKqueue: Boolean = false
    private var useEpoll: Boolean = false
    private var bufferSize: Int = 4096
    private var alignment: Int = 512
    private var timeout: Long = 5000L
    private var retryCount: Int = 3

    fun uring() {
        useUring = true
        useKqueue = false
        useEpoll = false
    }

    fun kqueue() {
        useUring = false
        useKqueue = true
        useEpoll = false
    }

    fun epoll() {
        useUring = false
        useKqueue = false
        useEpoll = true
    }

    fun scatterGather() {
        useScatterGather = true
    }

    fun unbuffered() {
        useUnbuffered = true
    }

    fun buffered() {
        useUnbuffered = false
    }

    fun mmap() {
        useMmap = true
    }

    fun batch(size: Int) {
        batchSize = size
    }

    fun queue(depth: Int) {
        queueDepth = depth
    }

    fun buffer(size: Int) {
        bufferSize = size
    }

    fun align(bytes: Int) {
        alignment = bytes
    }

    fun timeout(ms: Long) {
        timeout = ms
    }

    fun retry(count: Int) {
        retryCount = count
    }

    fun build(): AsyncIOConfig = AsyncIOConfig(
        useUring = useUring,
        useScatterGather = useScatterGather,
        useUnbuffered = useUnbuffered,
        batchSize = batchSize,
        queueDepth = queueDepth,
        useMmap = useMmap,
        useKqueue = useKqueue,
        useEpoll = useEpoll,
        bufferSize = bufferSize,
        alignment = alignment,
        timeout = timeout,
        retryCount = retryCount
    )
}

/**
 * Create async I/O configuration using DSL
 */
fun asyncIO(block: AsyncIOConfigDSL.() -> Unit): AsyncIOConfig {
    val dsl = AsyncIOConfigDSL()
    dsl.block()
    return dsl.build()
}

// === ASYNC I/O WRITER ===

/**
 * Modern async I/O writer with IO_URING support
 */
class AsyncIOWriter(private val config: AsyncIOConfig) {

    fun writeIsamData(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        when {
            config.useUring -> writeWithUring(cursor, datafilename, meta)
            config.useKqueue -> writeWithKqueue(cursor, datafilename, meta)
            config.useEpoll -> writeWithEpoll(cursor, datafilename, meta)
            config.useMmap -> writeWithMmap(cursor, datafilename, meta)
            else -> writeWithBuffered(cursor, datafilename, meta)
        }
    }

    fun appendIsamData(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        when {
            config.useUring -> appendWithUring(msf, datafilename, varChars, transform)
            config.useKqueue -> appendWithKqueue(msf, datafilename, varChars, transform)
            config.useEpoll -> appendWithEpoll(msf, datafilename, varChars, transform)
            config.useMmap -> appendWithMmap(msf, datafilename, varChars, transform)
            else -> appendWithBuffered(msf, datafilename, varChars, transform)
        }
    }

    private fun writeWithUring(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        // IO_URING implementation with scatter-gather support
        val fd = open(datafilename, O_CREAT or O_WRONLY, 644.fromOctal())
        
        memScoped {
            // Initialize io_uring
            val ring = alloc<io_uring>()
            val result = io_uring_queue_init(config.queueDepth, ring.ptr, 0)
            require(result == 0) { "Failed to initialize io_uring: $result" }

            try {
                val rowLen = meta.last().end
                val rowBuffer = ByteArray(rowLen)

                // Batch processing with IO_URING
                for (batchStart in 0 until cursor.a step config.batchSize) {
                    val batchEnd = minOf(batchStart + config.batchSize, cursor.a)
                    val batchSize = batchEnd - batchStart

                    // Prepare batch of submissions
                    for (i in 0 until batchSize) {
                        val rowIndex = batchStart + i
                        val rowData = cursor.row(rowIndex).left

                        // Prepare row buffer
                        for (x in 0 until meta.size) {
                            val colMeta = meta[x]
                            val colData = rowData[x]
                            val colBytes = colMeta.encoder(colData)
                            colBytes.copyInto(rowBuffer, colMeta.begin, 0, colBytes.size)
                            
                            if (colMeta.type.networkSize == null && colBytes.size < colMeta.end - colMeta.begin) {
                                rowBuffer[colMeta.begin + colBytes.size] = 0
                            }
                        }

                        // Get submission queue entry
                        val sqe = io_uring_get_sqe(ring.ptr)
                        require(sqe != null) { "Failed to get SQE" }

                        val offset = (rowIndex * rowLen).toULong()
                        
                        if (config.useScatterGather) {
                            // Use scatter-gather I/O
                            val iovec = alloc<iovec> {
                                iov_base = rowBuffer.refTo(0).getPointer(iovec::iov_base)
                                iov_len = rowLen.toULong()
                            }
                            io_uring_prep_writev(sqe, fd, iovec.ptr, 1, offset)
                        } else {
                            // Use regular write
                            io_uring_prep_write(sqe, fd, rowBuffer.refTo(0), rowLen.toUInt(), offset)
                        }
                    }

                    // Submit batch
                    val submitted = io_uring_submit(ring.ptr)
                    require(submitted == batchSize) { "Expected $batchSize submissions, got $submitted" }

                    // Wait for completions
                    for (i in 0 until batchSize) {
                        val cqe = alloc<CPointerVar<io_uring_cqe>>()
                        val waitResult = io_uring_wait_cqe(ring.ptr, cqe.ptr)
                        require(waitResult == 0) { "Failed to wait for CQE: $waitResult" }

                        val completion = cqe.value!!
                        require(completion.pointed.res == rowLen) { "Write failed: ${completion.pointed.res}"
                        }

                        io_uring_cqe_seen(ring.ptr, completion)
                    }
                }
            } finally {
                io_uring_queue_exit(ring.ptr)
                close(fd)
            }
        }
    }

    private fun writeWithKqueue(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        // kqueue implementation for POSIX systems
        TODO("Implement kqueue-based async I/O")
    }

    private fun writeWithEpoll(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        // epoll implementation for Linux
        TODO("Implement epoll-based async I/O")
    }

    private fun writeWithMmap(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        // mmap-based implementation
        TODO("Implement mmap-based I/O")
    }

    private fun writeWithBuffered(cursor: Cursor, datafilename: String, meta: Indexed<RecordMeta>) {
        // Buffered I/O implementation
        TODO("Implement buffered I/O")
    }

    private fun appendWithUring(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        // IO_URING append implementation
        TODO("Implement IO_URING append")
    }

    private fun appendWithKqueue(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        // kqueue append implementation
        TODO("Implement kqueue append")
    }

    private fun appendWithEpoll(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        // epoll append implementation
        TODO("Implement epoll append")
    }

    private fun appendWithMmap(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        // mmap append implementation
        TODO("Implement mmap append")
    }

    private fun appendWithBuffered(
        msf: Iterable<RowVec>,
        datafilename: String,
        varChars: Map<String, Int>,
        transform: ((RowVec) -> RowVec)?
    ) {
        // Buffered append implementation
        TODO("Implement buffered append")
    }
}

// === UTILITY EXTENSIONS ===

fun Int.fromOctal(): Int = this

fun Long.humanReadableByteCountIEC(): String {
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var bytes = this
    var unit = 0
    while (bytes >= 1024 && unit < units.size - 1) {
        bytes /= 1024
        unit++
    }
    return "$bytes ${units[unit]}"
}

fun Long.humanReadableByteCountSI(): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    var bytes = this
    var unit = 0
    while (bytes >= 1000 && unit < units.size - 1) {
        bytes /= 1000
        unit++
    }
    return "$bytes ${units[unit]}"
} 