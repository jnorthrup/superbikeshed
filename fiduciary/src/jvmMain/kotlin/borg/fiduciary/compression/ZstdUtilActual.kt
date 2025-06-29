package borg.fiduciary.compression

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

actual object ZstdUtil {
    actual val DEFAULT_COMPRESSION_LEVEL: Int = 3 // Standard default for zstd-jni

    actual fun compress(source: InputStream, destination: OutputStream, level: Int) {
        try {
            // Apply buffering to the destination stream before passing to ZstdOutputStream
            // and to the source stream before reading.
            ZstdOutputStream(destination.buffered(DEFAULT_BUFFER_SIZE), level).use { zos ->
                source.buffered(DEFAULT_BUFFER_SIZE).use { bufferedSource ->
                    bufferedSource.copyTo(zos, DEFAULT_BUFFER_SIZE) // Standard Kotlin extension for InputStream
                }
            }
        } catch (e: Exception) {
            throw IOException("Zstd compression failed: ${e.message}", e)
        }
    }

    actual fun decompress(source: InputStream, destination: OutputStream) {
        try {
            // Apply buffering to the source stream before passing to ZstdInputStream
            // and to the destination stream before writing.
            ZstdInputStream(source.buffered(DEFAULT_BUFFER_SIZE)).use { zis ->
                destination.buffered(DEFAULT_BUFFER_SIZE).use { bufferedDest ->
                    zis.copyTo(bufferedDest, DEFAULT_BUFFER_SIZE) // Standard Kotlin extension for InputStream
                }
            }
        } catch (e: Exception) {
            throw IOException("Zstd decompression failed: ${e.message}", e)
        }
    }
}
