package borg.trikeshed.zlib

import kotlin.test.*
import borg.trikeshed.lib.*

class ZlibLz4ZstdTest {
    private fun randomBytes(size: Int): Indexed<Byte> =
        size j { (it * 31 + 17).toByte() }

    @Test
    fun zlib_roundtrip() {
        val input = randomBytes(1024)
        val compressed = Zlib.compress(input)
        val decompressed = Zlib.decompress(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun zlib_gzip_roundtrip() {
        val input = randomBytes(1024)
        val compressed = Zlib.gzipCompress(input)
        val decompressed = Zlib.decompress(compressed) // Assuming decompress handles gzip
        assertContentEquals(input, decompressed)
    }

    @Test
    fun lz4_roundtrip() {
        val input = randomBytes(1024)
        val compressed = Lz4.compressFrame(input)
        val decompressed = Lz4.decompressFrame(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun zstd_roundtrip() {
        val input = randomBytes(1024)
        val compressed = Zstd.compress(input)
        val decompressed = Zstd.decompress(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun zlib_empty() {
        val input = 0 j { 0.toByte() }
        val compressed = Zlib.compress(input)
        val decompressed = Zlib.decompress(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun lz4_empty() {
        val input = 0 j { 0.toByte() }
        val compressed = Lz4.compressFrame(input)
        val decompressed = Lz4.decompressFrame(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun zstd_empty() {
        val input = 0 j { 0.toByte() }
        val compressed = Zstd.compress(input)
        val decompressed = Zstd.decompress(compressed)
        assertContentEquals(input, decompressed)
    }

    @Test
    fun zlib_corrupt() {
        val input = randomBytes(32)
        val corrupt = input.map { (it xor 0xFF.toByte()) }.toIndexed()
        assertFailsWith<Throwable> { Zlib.decompress(corrupt) }
    }

    @Test
    fun lz4_corrupt() {
        val input = randomBytes(32)
        val corrupt = input.map { (it xor 0xFF.toByte()) }.toIndexed()
        assertFailsWith<Throwable> { Lz4.decompressFrame(corrupt) }
    }

    @Test
    fun zstd_corrupt() {
        val input = randomBytes(32)
        val corrupt = input.map { (it xor 0xFF.toByte()) }.toIndexed()
        assertFailsWith<Throwable> { Zstd.decompress(corrupt) }
    }
} 