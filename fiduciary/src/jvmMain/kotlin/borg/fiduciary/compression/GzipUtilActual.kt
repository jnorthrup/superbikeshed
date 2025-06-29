package borg.fiduciary.compression

import okio.Sink
import okio.Source
import okio.buffer
import okio.gzip
import java.io.IOException

actual object GzipUtil {
    actual fun compress(source: Source, sink: Sink) {
        try {
            sink.gzip().buffer().use { gzipSink ->
                source.buffer().use { bufferedSource ->
                    bufferedSource.readAll(gzipSink)
                }
            }
        } catch (e: Exception) {
            // Consider more specific error handling or re-throwing as a custom compression exception
            throw IOException("Gzip compression failed: ${e.message}", e)
        }
    }

    actual fun decompress(source: Source, sink: Sink) {
        try {
            source.gzip().buffer().use { gzipSource ->
                sink.buffer().use { bufferedSink ->
                    gzipSource.readAll(bufferedSink)
                }
            }
        } catch (e: Exception) {
            // Consider more specific error handling or re-throwing
            throw IOException("Gzip decompression failed: ${e.message}", e)
        }
    }
}
