package fiduciary

import fiduciary.fetch.*
import borg.trikeshed.lib.*
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

suspend fun main() {
    val ioContext = IOContext.NioContext("extract-0720")
    val httpClient = HttpClientBuilder()
        .ioContext(ioContext)
        .build()
    
    val fetcher = ZipRangeFetcher(httpClient)
    val centralDirs = fetcher.fetchZipCentralDirs()
    
    // Find patrick0720.txt in central directories
    centralDirs.forEach { centralDir ->
        val entry = centralDir.entries.find { 
            it.filename.contains("0720") || it.filename.contains("patrick0720")
        }
        
        if (entry != null) {
            println("Found ${entry.filename} in ${centralDir.archiveName}")
            println("  Offset: ${entry.localHeaderOffset}")
            println("  Compressed size: ${entry.compressedSize}")
            println("  Uncompressed size: ${entry.uncompressedSize}")
            println("  Method: ${entry.method}")
            
            // Fetch the compressed data using range request
            val compressedData = fetchCompressedEntry(
                httpClient, 
                centralDir.archiveUrl,
                entry.localHeaderOffset,
                entry.compressedSize
            )
            
            // Decompress based on method (0=stored, 8=deflated)
            val uncompressedData = when (entry.method) {
                0 -> compressedData // Stored (no compression)
                8 -> inflateData(compressedData) // Deflated
                else -> throw Exception("Unsupported compression method: ${entry.method}")
            }
            
            // Save the file
            val content = String(uncompressedData)
            println("\nContent (first 500 chars):")
            println(content.take(500))
            
            // Write to file
            java.io.File("patrick0720.txt").writeText(content)
            println("\nSaved to patrick0720.txt")
            return
        }
    }
    
    println("patrick0720.txt not found in archives")
}

suspend fun fetchCompressedEntry(
    httpClient: HttpClient,
    archiveUrl: String, 
    offset: Long,
    compressedSize: Long
): ByteArray {
    // ZIP local file header is typically 30 bytes + filename + extra field
    // We need to fetch and parse it first to get exact data offset
    val headerSize = 1024L // Fetch extra to ensure we get the full header
    
    val request = HttpRequest(
        method = HttpMethod.GET,
        path = HttpRequestPath(archiveUrl),
        headers = 2 j { i ->
            when (i) {
                0 -> HttpHeaderName("Range") j HttpHeaderValue("bytes=$offset-${offset + headerSize + compressedSize}")
                1 -> HttpHeaderName("User-Agent") j HttpHeaderValue("Extract0720/1.0")
                else -> throw IndexOutOfBoundsException()
            }
        }
    )
    
    val response = httpClient.execute(request)
    if (response.status.code != 206) {
        throw Exception("Range request failed: ${response.status.code}")
    }
    
    val data = response.body.toByteArray()
    
    // Parse local file header to find data offset
    val nameLen = data[26].toInt() and 0xFF or ((data[27].toInt() and 0xFF) shl 8)
    val extraLen = data[28].toInt() and 0xFF or ((data[29].toInt() and 0xFF) shl 8)
    val dataOffset = 30 + nameLen + extraLen
    
    // Extract just the compressed data
    return data.sliceArray(dataOffset until (dataOffset + compressedSize.toInt()))
}

fun inflateData(compressedData: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(compressedData)
    
    val outputStream = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        outputStream.write(buffer, 0, count)
    }
    
    inflater.end()
    return outputStream.toByteArray()
}