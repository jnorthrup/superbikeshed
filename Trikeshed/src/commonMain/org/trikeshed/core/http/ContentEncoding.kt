package org.trikeshed.core.http

import org.trikeshed.core.Series
import org.trikeshed.core.mime.MimeTypes
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import kotlin.String
import kotlin.Boolean
import kotlin.Pair

object ContentEncodingHandler {
    private const val GZIP_SUFFIX = ".gz"
    
    val GZIP = ContentEncoding("gzip")
    val IDENTITY = ContentEncoding("identity")
    
    fun createGzipPath(path: FilePath): GzipPath {
        return GzipPath(path.value + GZIP_SUFFIX)
    }
    
    fun isGzipPath(path: FilePath): Boolean {
        return path.value.endsWith(GZIP_SUFFIX)
    }
    
    fun stripGzipExtension(path: FilePath): FilePath {
        return if (isGzipPath(path)) {
            FilePath(path.value.substring(0, path.value.length - GZIP_SUFFIX.length))
        } else {
            path
        }
    }
    
    fun compress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(data)
        }
        return output.toByteArray()
    }
    
    fun decompress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
            gzip.copyTo(output)
        }
        return output.toByteArray()
    }
    
    fun shouldCompress(mimeType: MimeTypes): Boolean {
        return MimeTypes.isCompressible(mimeType)
    }
    
    fun negotiateEncoding(acceptEncodings: AcceptEncodings, contentType: ContentType): ContentEncoding? {
        val mimeType = MimeTypes.values().find { it.mimeType == contentType.value } ?: MimeTypes.UNKNOWN
        
        return if (shouldCompress(mimeType) && acceptEncodings.▶.any { it.value == GZIP.value }) {
            GZIP
        } else {
            IDENTITY
        }
    }
    
    fun lazyCreateGzip(
        path: FilePath,
        reader: FileReader,
        writer: FileWriter,
        exists: FileExists
    ): GzipPath {
        val gzipPath = createGzipPath(path)
        
        if (!exists(gzipPath)) {
            val data = reader(path)
            val compressed = compress(data)
            writer(gzipPath, compressed)
        }
        
        return gzipPath
    }
    
    fun serveContent(
        path: FilePath,
        contentType: ContentType,
        acceptEncodings: AcceptEncodings,
        reader: FileReader,
        writer: FileWriter,
        exists: FileExists
    ): Pair<ByteArray, ContentEncoding> {
        val mimeType = MimeTypes.values().find { it.mimeType == contentType.value } ?: MimeTypes.UNKNOWN
        val encoding = negotiateEncoding(acceptEncodings, contentType)
        
        return when (encoding) {
            GZIP -> {
                val gzipPath = lazyCreateGzip(path, reader, writer, exists)
                reader(gzipPath) to GZIP
            }
            else -> reader(path) to IDENTITY
        }
    }
} 