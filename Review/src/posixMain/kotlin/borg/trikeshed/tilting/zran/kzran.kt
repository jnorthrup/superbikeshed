package borg.trikeshed.tilting.zran

import borg.trikeshed.lib.*
import borg.trikeshed.native.HasPosixErr
import kotlinx.cinterop.*
import platform.zlib.*

class GzIndex : HasPosixErr {
    var list: Series<Point> = emptySeries()
    var length: ULong = 0uL

    fun build(file: CPointer<FILE>, span: Long): Int {
        var ret: Int
        var totin: ULong = 0uL
        var totout: ULong = 0uL
        var last: ULong = 0uL
        val input = ByteArray(CHUNK)
        val window = ByteArray(WINSIZE)

        memScoped {
            val strm = alloc<z_stream>()
            strm.zalloc = null
            strm.zfree = null
            strm.opaque = null
            strm.avail_in = 0u
            strm.next_in = null

            ret = inflateInit2(strm.ptr, 47)
            posixRequires(ret == Z_OK) { "inflateInit2 failed: $ret" }

            val tbuf = input.pin()
            val wbuf = window.pin()

            do {
                if (strm.avail_in == 0u) {
                    strm.avail_in = fread(tbuf.addressOf(0), 1u, CHUNK.toULong(), file).toUInt()
                    if (ferror(file) != 0) {
                        ret = Z_ERRNO
                        break
                    }
                    if (strm.avail_in == 0u) {
                        ret = Z_STREAM_END
                        break
                    }
                    strm.next_in = tbuf.addressOf(0).reinterpret()
                    totin += strm.avail_in.toULong()
                }

                do {
                    if (totout == 0uL || totout - last > span.toULong()) {
                        list = list + s_[Point(
                            out = totout,
                            `in` = totin - strm.avail_in.toULong(),
                            bits = strm.data_type and 7,
                            window = window.copyOf()
                        )]
                        last = totout
                    }

                    strm.avail_out = WINSIZE.toUInt()
                    strm.next_out = wbuf.addressOf(0).reinterpret()
                    ret = inflate(strm.ptr, Z_NO_FLUSH)

                    when (ret) {
                        Z_NEED_DICT -> ret = Z_DATA_ERROR
                        Z_MEM_ERROR, Z_DATA_ERROR -> break
                    }

                    totout += (WINSIZE - strm.avail_out.toInt()).toULong()
                } while (strm.avail_out == 0u)
            } while (ret != Z_STREAM_END)

            inflateEnd(strm.ptr)

            posixRequires(ret == Z_STREAM_END || ret == Z_OK) { "Error during inflation: $ret, expected Z_OK or Z_STREAM_END" }
            length = totout
            return list.size // Assuming Series has a size property
        }
    }

    fun readIndex(filename: String): Int {
        memScoped {
            val file = fopen(filename, "rb") ?: return -1

            try {
                val pointCount = alloc<IntVar>()
                if (fread(pointCount.ptr, sizeOf<IntVar>().toULong(), 1u, file) != 1u) return -1

                val pointOutputSeries: Series<Point> = Series.of(*(Array(pointCount.value) { Point() })) // Convert Array to Series
                var windowSizesSeries: Series<UShort> = emptySeries()

                for (i in 0 until pointCount.value) {
                    val point = pointOutputSeries[i]
                    if (fread(point.out.ptr, sizeOf<ULongVar>().toULong(), 1u, file) != 1u) return -1
                    if (fread(point.`in`.ptr, sizeOf<ULongVar>().toULong(), 1u, file) != 1u) return -1
                    
                    val windowSize = alloc<UShortVar>()
                    if (fread(windowSize.ptr, sizeOf<UShortVar>().toULong(), 1u, file) != 1u) return -1
                    windowSizesSeries = windowSizesSeries + s_[windowSize.value]
                }

                list = emptySeries()

                // Corrected windowOffsets to use ULong for positions to avoid overflow
                val windowOrigin: ULong = (4uL + pointCount.value.toULong() * (ULong.SIZE_BYTES.toULong() * 2uL + UShort.SIZE_BYTES.toULong()))
                val windowOffsets: Series<ULong> =
                    combine(s_[0uL, windowOrigin], windowSizesSeries α { it.toULong() })
                        .zipWithNext() α { it.a + it.b } // Assuming Series.zipWithNext() and α for transformations

                for (i in 0 until pointCount.value) {
                    val point = pointOutputSeries[i]
                    val windowSize = windowSizesSeries[i].toInt()
                    val window = ByteArray(windowSize)
                    
                    fseek(file, windowOffsets[i].toLong(), SEEK_SET)
                    if (fread(window.pin().addressOf(0), 1u, windowSize.toULong(), file) != windowSize.toULong()) return -1
                    
                    list = list + s_[Point(
                        out = point.out,
                        `in` = point.`in`,
                        bits = 0,
                        window = window
                    )]
                }

                return list.size
            } finally {
                fclose(file)
            }
        }
    }

    fun decode(file: CPointer<FILE>, offset: ULong, len: Int): Sequence<Byte> = sequence {
        val point = list.lastOrNull { it.out <= offset } ?: return@sequence
        
        memScoped {
            val strm = alloc<z_stream>()
            strm.zalloc = null
            strm.zfree = null
            strm.opaque = null
            strm.avail_in = 0u
            strm.next_in = null

            var ret = inflateInit2(strm.ptr, -15)
            posixRequires(ret == Z_OK) { "inflateInit2 failed: $ret" }

            if (point.window.isNotEmpty()) {
                ret = inflateSetDictionary(strm.ptr, point.window.pin().addressOf(0).reinterpret(), point.window.size.toUInt())
                posixRequires(ret == Z_OK) { "inflateSetDictionary failed: $ret" }
            }

            fseek(file, point.`in`.toLong(), SEEK_SET)
            
            val input = ByteArray(CHUNK)
            val buf = ByteArray(WINSIZE)
            val tbuf = input.pin()
            val obuf = buf.pin() // Pin the actual output buffer
            var skip = offset - point.out
            var have = 0
            var fail = false

            do {
                if (strm.avail_in == 0u) {
                    strm.avail_in = fread(tbuf.addressOf(0), 1u, CHUNK.toULong(), file).toUInt()
                    if (ferror(file) != 0) break
                    if (strm.avail_in == 0u) break
                    strm.next_in = tbuf.addressOf(0).reinterpret()
                }

                do {
                    strm.avail_out = buf.size.toUInt()
                    strm.next_out = obuf.addressOf(0) // Use the pinned output buffer
                    val ret = inflate(strm.ptr, Z_NO_FLUSH)
                    if (ret != Z_OK && ret != Z_STREAM_END) fail = true // Allow Z_STREAM_END as termination
                    if (!fail)
                        for (i in 0 until buf.size - strm.avail_out.toInt())
                            this.yield(buf[i])
                } while (strm.avail_out == 0u && !fail)
            } while (!fail)

            inflateEnd(strm.ptr)
        }
    }

    companion object {
        const val WINSIZE = 32768
        const val CHUNK = 16384
    }
}

data class Point(
    var out: ULong = 0uL,
    var `in`: ULong = 0uL,
    var bits: Int = 0,
    var window: ByteArray = byteArrayOf()
)
