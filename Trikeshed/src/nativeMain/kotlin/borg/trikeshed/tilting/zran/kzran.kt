@file:OptIn(ExperimentalUnsignedTypes::class, ExperimentalForeignApi::class, ExperimentalForeignApi::class,
    ExperimentalForeignApi::class, ExperimentalForeignApi::class
)

package borg.trikeshed.tilting.zran

import borg.trikeshed.common.collections.binarySearch
import borg.trikeshed.isam.meta.PlatformCodec.Companion.currentPlatformCodec.readULong
import borg.trikeshed.isam.meta.PlatformCodec.Companion.currentPlatformCodec.readUShort
import borg.trikeshed.isam.meta.PlatformCodec.Companion.currentPlatformCodec.writeULong
import borg.trikeshed.isam.meta.PlatformCodec.Companion.currentPlatformCodec.writeUShort
import borg.trikeshed.lib.*
import borg.trikeshed.lib.CZero.nz
import borg.trikeshed.lib.CZero.z
import borg.trikeshed.native.HasPosixErr.Companion.posixFailOn
import borg.trikeshed.native.HasPosixErr.Companion.posixRequires
import kotlinx.cinterop.*
import platform.posix.*
import platform.zlib.*
import kotlin.math.max


const val RAW = -15
const val ZLIB = 15
const val GZIP = 31
const val WINSIZE = 32768U
const val CHUNK = 16384
val __usSz = UShort.SIZE_BYTES.toULong()
val __ulSz = ULong.SIZE_BYTES.toULong()

@ExperimentalUnsignedTypes
class GzIndex {
    val have: Int get() = list.size
    val mode: Int = 0
    var length: ULong = 0u
    var list: MutableList<Point> = mutableListOf()

    /** The name of the index file, or null if it's stdin */
    var fpName: String? = null

    /** The 1-shot index file pointer, or stdin if fpName is null */
    val indexFp: CPointer<FILE> by lazy {
        val name = fpName ?: "-"

        val fp = fpName?.let { fopen(name, "rb") } ?: stdin
        posixRequires(fp != null) { "Error: could not open index file $name" }
        fp!!
    }


    fun getWindow(index: Int): UByteArray {
        if (index < 0 || index >= list.size) {
            throw IndexOutOfBoundsException("Index out of bounds: $index")
        }
        return list[index].window
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun build(gzFile: CPointer<FILE>, span: ULong): Int {
        val strm = nativeHeap.alloc<z_stream>()
        val strmp: z_streamp = strm.ptr
        val buf = UByteArray(CHUNK)
        val win = UByteArray(WINSIZE.toInt())
        var totin = 0
        var totout = 0
        var mode = 0
        var ret: Int = 0
        var last: ULong = 0U
        val stride0 = CHUNK
        var stride = stride0
        do {
            val pinnedBuf = buf.pin()
            if (strm.avail_in == 0u) {
                val pinnedBufAddr = pinnedBuf.addressOf(0)
                strm.avail_in = fread(pinnedBufAddr, 1u, stride.toULong(), gzFile).toUInt()
                totin += strm.avail_in.toInt()
                strm.next_in = pinnedBufAddr
                if (strm.avail_in < stride.toUInt() && ferror(gzFile).nz) {
                    ret = Z_ERRNO
                    break
                }

                if (mode == 0) {
                    mode =
                        if (strm.avail_in == 0u) RAW else if ((strm.next_in!![0] and 15u) == 8.toUByte())
                            ZLIB else if (strm.next_in!![0] == 0x1f.toUByte()) GZIP else RAW
                    ret = inflateInit2(strm.ptr, mode)
                    if (ret != Z_OK)
                        break
                }
            }

            if (strm.avail_out.z) {
                strm.avail_out = WINSIZE
                strm.next_out = win.pin().addressOf(0)
            }


            if (mode == RAW && list.isEmpty()) strm.data_type = 0x80 else {
                val before = strm.avail_out
                ret = inflate(strm.ptr, Z_BLOCK)
                totout += before.toInt() - strm.avail_out.toInt()
            }

            if ((strm.data_type and 0xc0) == 0x80 && (list.isEmpty() || totout.toULong() - last >= span)) {
                val bits = strm.data_type and 7
                if (bits.z) stride = stride0
                else {
                    stride = 1
                    continue
                }
                val deflater = nativeHeap.alloc<z_stream>()
                var ret1 = deflateInit(deflater.ptr, Z_BEST_COMPRESSION)
                var compressedData = UByteArray(32 shl 10 /* 32K */)
                val compressedDataBuffer = compressedData.pin()
                deflater.next_in = win.pin().addressOf(0)
                deflater.avail_in = WINSIZE
                deflater.next_out = compressedDataBuffer.addressOf(0)
                deflater.avail_out = compressedData.size.toUInt()
                ret1 = deflate(deflater.ptr, Z_FINISH)
                posixRequires(ret1 == Z_STREAM_END) { "Compressed data $ret1 isn't Z_STREAM_END (${Z_STREAM_END})" }
                deflateEnd(deflater.ptr)
                compressedDataBuffer.unpin()
                val compressedDataSize = compressedData.size - deflater.avail_out.toInt()
                posixRequires(compressedDataSize.toULong() < UShort.MAX_VALUE * 1UL) { "Compressed data size too large: $compressedDataSize" }

                compressedData = compressedData.sliceArray(0 until compressedDataSize)

                val point = Point((totin - strm.avail_in.toInt()).toULong(), totout.toULong(), compressedData)
                list += point
                last = totout.toULong()
            }
            if (ret == Z_STREAM_END && mode == GZIP && (strm.avail_in.nz || ungetc(getc(gzFile), gzFile) != EOF))
                ret = inflateReset2(strm.ptr, GZIP)

        } while (ret == Z_OK)

        inflateEnd(strm.ptr)

        posixRequires(ret != Z_STREAM_END) { "Error: $ret != Z_STREAM_END (${Z_STREAM_END})" }
        length = totout.toULong()
        return have
    }


    fun writeIndex(indexFname: String): Int {
        return withIndexFile(indexFname, "wb") { indexFp ->
            "kzra".encodeToByteArray()
                .usePinned { fwrite(it.addressOf(0), 1u, "kzra".encodeToByteArray().size.toULong(), indexFp) }
            list.forEach { writeULong(it.output).usePinned { fwrite(it.addressOf(0), 1u, __ulSz, indexFp) } }
            writeULong(ULong.MAX_VALUE).usePinned { fwrite(it.addressOf(0), 1u, __ulSz, indexFp) }
            list.forEach { writeULong(it.input).usePinned { fwrite(it.addressOf(0), 1u, __ulSz, indexFp) } }
            list.forEach { writeUShort(it.winsize).usePinned { fwrite(it.addressOf(0), 1u, __usSz, indexFp) } }
            list.forEach { it.window.apply { usePinned { fwrite(it.addressOf(0), 1u, size.toULong(), indexFp) } } }
            0
        }
    }


    /**
     * Read index from input given.  if the file is stdin, the assumption is the index block is known and the stream is
     * pre-seeked.  otherwise the index seeks the seekable file to the window start.
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun readIndex(indexFname: String?): Int = withIndexFile(indexFname, "rb") { indexFp ->
        val magic = UByteArray(4)
        magic.usePinned { fread(it.addressOf(0), 1u, 4u, indexFp) }
        val magicStr = magic.toByteArray().decodeToString()
        posixFailOn(magicStr != "kzra") { ("Error: invalid index file format: '$magicStr' is not 'kzra'") }
        val pointOutput = mutableListOf<ULong>()
        val pointInput = mutableListOf<ULong>()
        val windowSizes = mutableListOf<UShort>()
        val buf = ByteArray(ULong.SIZE_BYTES)

        buf.usePinned { tempOutput ->
            val __ptr = tempOutput.addressOf(0)
            while (true) {
                fread(__ptr, __ulSz, 1u, indexFp)
                val uLong = readULong(buf)
                if (uLong == ULong.MAX_VALUE) break
                pointOutput.add(uLong)
            }

            for (i in pointOutput.indices) {
                fread(__ptr, __ulSz, 1u, indexFp)
                pointInput.add(readULong(buf))
            }

            for (i in pointOutput.indices) {
                fread(__ptr, __usSz, 1u, indexFp)
                val uShort = readUShort(buf)
                windowSizes.add(uShort)
            }
        }

        list.clear()

        val windowOrigin = (4 + pointOutput.size * (ULong.SIZE_BYTES * 2 + UShort.SIZE_BYTES)).toULong()
        val windowOffsets =
            (listOf(0.toUShort(), windowOrigin.toUShort()) + windowSizes).zipWithNext().map { it.first + it.second }
                .toMutableList()


        val isStdin = (indexFname == "-")

        for (i: Int in pointOutput.indices)
            list += Point(pointOutput[i], pointInput[i], UByteArray(0),
                windowSupplier = if (isStdin) {
                    val window = UByteArray(windowSizes[i].toInt())
                    window.usePinned { fread(it.addressOf(0), 1u, windowSizes[i].toULong(), stdin) }
                    window.`↺`
                } else fun(): UByteArray {
                    return withIndexFile(indexFname) { indexFp ->
                        fseek(indexFp, windowOffsets[i].toLong(), SEEK_SET)
                        val window = UByteArray(windowSizes[i].toInt())
                        window.usePinned {
                            fread(it.addressOf(0), 1u, windowSizes[i].toULong(), indexFp)
                        }
                        window
                    }
                }
            )
        0
    }

    /**
     * opens the index file
     */
    private fun <T> withIndexFile(indexFname: String?, mode: String = "rb", block: (CPointer<FILE>) -> T): T {
        try {
            return if (indexFname == null || indexFname == "-") {
                block(stdin!!)
            } else {
                val indexFp = fopen(indexFname, mode)
                posixFailOn(indexFp == null) { ("Error: could not open index file $indexFname") }
                block(indexFp!!)
            }
        } finally {
            fclose(indexFp)
        }
    }

    /**
     * inflate the index window that is stored in the window field of the point.
     *
     * @param index the index of the point to inflate
     * @return the z_stream that is primed with 32k of data
     */
    fun prepareIndexEntry(index: Int): z_stream {
        val strm = nativeHeap.alloc<z_stream>()
        val windowSize = list[index].winsize
        val orign_window = list[index].window
        logDebug {
            val bytes = orign_window.toSeries()
            "--- before inflateIndexWindow: windowSize=$windowSize, orign_window=${bytes}"
        }
        orign_window.usePinned { window ->
            val throwaway = UByteArray(32 shl 10)
            val throwawaySize = throwaway.size.toUInt()

            throwaway.usePinned { tbuf ->
                inflateInit(strm.ptr)
                strm.avail_in = windowSize.toUInt()
                strm.next_in = window.addressOf(0)
                strm.avail_out = throwawaySize
                strm.next_out = tbuf.addressOf(0)
                val ret = inflate(strm.ptr, Z_NO_FLUSH)
                logDebug {
                    val bytes = throwaway.toSeries()
                    "+++ after inflateIndexWindow: ret=$ret, throwaway=${bytes}"
                }
                posixRequires(ret == Z_STREAM_END) { "Error: inflate failed: $ret not Z_STREAM_END (${Z_STREAM_END})" }
            }
        }
        return strm
    }
}
