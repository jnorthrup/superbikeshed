package borg.trikeshed.zip

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.toIndexed
import borg.trikeshed.net.quic.QuicConnection
import borg.trikeshed.net.quic.QuicStream

class QuicRemoteFileAccessor(
    private val remoteFileUrl: String,
    private val quicConnection: QuicConnection
) : RemoteFileAccessor {

    override suspend fun getFileSize(): Long {
        val stream = quicConnection.createStream() ?: throw Exception("Failed to create QUIC stream for HEAD request")
        try {
            val request = "HEAD $remoteFileUrl HTTP/1.1\r\nHost: ${remoteFileUrl.substringAfter("://").substringBefore("/")}\r\n\r\n"
            stream.writeBytes(request.encodeToByteArray().toIndexed())

            val responseHeaders = readHttpResponseHeaders(stream)
            val contentLength = responseHeaders["Content-Length"]?.toLongOrNull()

            if (contentLength == null) {
                throw Exception("Content-Length header not found in HEAD response")
            }
            return contentLength
        } finally {
            stream.close()
        }
    }

    override suspend fun readRange(startByte: Long, endByte: Long): Indexed<Byte> {
        val stream = quicConnection.createStream() ?: throw Exception("Failed to create QUIC stream for GET request")
        try {
            val request = "GET $remoteFileUrl HTTP/1.1\r\nHost: ${remoteFileUrl.substringAfter("://").substringBefore("/")}\r\nRange: bytes=$startByte-$endByte\r\n\r\n"
            stream.writeBytes(request.encodeToByteArray().toIndexed())

            // Read and discard headers until empty line (end of headers)
            val responseHeaders = readHttpResponseHeaders(stream)

            // Read the actual bytes of the range
            val buffer = mutableListOf<Byte>()
            while (true) {
                val byte = stream.readByte()
                if (byte == null) break
                buffer.add(byte)
            }
            return buffer.toIndexed()
        } finally {
            stream.close()
        }
    }

    private suspend fun readHttpResponseHeaders(stream: QuicStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val headerBuffer = mutableListOf<Byte>()
        while (true) {
            val byte = stream.readByte() ?: break
            headerBuffer.add(byte)
            if (headerBuffer.size >= 4 && headerBuffer.takeLast(4) == listOf<Byte>('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())) {
                // End of headers
                break
            }
        }
        val headerString = headerBuffer.toByteArray().decodeToString()
        headerString.split("\r\n").forEach { line ->
            val parts = line.split(":", 2)
            if (parts.size == 2) {
                headers[parts[0].trim()] = parts[1].trim()
            }
        }
        return headers
    }

    // Extension to read a single byte from QuicStream (blocking until byte is available or stream ends)
    private suspend fun QuicStream.readByte(): Byte? {
        val buffer = ByteArray(1)
        val bytesRead = this.readBytes(buffer.toIndexed())
        return if (bytesRead == 1) buffer[0] else null
    }
}