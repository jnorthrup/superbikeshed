import java.net.URL
import java.net.HttpURLConnection
import java.util.zip.Inflater
import java.io.File

fun main() {
    val url = "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip"
    
    // Known location from our search
    val filename = "Patrick Devine files/01-Transcripts/patrick_0720.txt"
    val localHeaderOffset = 3050270003L // Correct offset
    val compressedSize = 9612
    val uncompressedSize = 25389
    
    println("Extracting: $filename")
    println("Local header offset: $localHeaderOffset")
    println("Compressed size: $compressedSize bytes")
    println("Uncompressed size: $uncompressedSize bytes")
    
    // Fetch local file header + compressed data
    val conn = URL(url).openConnection() as HttpURLConnection
    val rangeEnd = localHeaderOffset + 30 + 1024 + compressedSize // header + extra + data
    conn.setRequestProperty("Range", "bytes=$localHeaderOffset-$rangeEnd")
    conn.setRequestProperty("User-Agent", "Patrick0720Extractor/1.0")
    
    if (conn.responseCode != 206) {
        println("Range request failed: ${conn.responseCode}")
        return
    }
    
    val data = conn.inputStream.readBytes()
    conn.disconnect()
    
    println("\nFetched ${data.size} bytes")
    
    // Verify local file header signature (0x04034b50)
    if (data[0] == 0x50.toByte() && data[1] == 0x4B.toByte() && 
        data[2] == 0x03.toByte() && data[3] == 0x04.toByte()) {
        println("✓ Valid local file header")
    } else {
        println("✗ Invalid local file header")
        return
    }
    
    // Parse local file header
    val method = readShort(data, 8)
    val nameLen = readShort(data, 26)
    val extraLen = readShort(data, 28)
    
    println("Compression method: $method")
    println("Filename length: $nameLen")
    println("Extra field length: $extraLen")
    
    // Extract compressed data
    val dataOffset = 30 + nameLen + extraLen
    val compressedData = data.sliceArray(dataOffset until (dataOffset + compressedSize))
    
    println("\nDecompressing...")
    
    // Decompress
    val uncompressedData = if (method == 8) {
        // Deflate
        val inflater = Inflater(true) // nowrap=true for raw deflate
        inflater.setInput(compressedData)
        val output = ByteArray(uncompressedSize)
        val result = inflater.inflate(output)
        inflater.end()
        println("Inflated $result bytes")
        output
    } else {
        // Stored
        compressedData
    }
    
    // Save to file
    val outputFile = File("patrick_0720.txt")
    outputFile.writeBytes(uncompressedData)
    
    println("\n✅ SUCCESS! Saved to: ${outputFile.absolutePath}")
    println("File size: ${outputFile.length()} bytes")
    
    // Show preview
    val text = String(uncompressedData)
    println("\n--- PREVIEW ---")
    println(text.take(1000))
    if (text.length > 1000) {
        println("\n... (${text.length - 1000} more characters)")
    }
}

fun readShort(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or 
           ((data[offset + 1].toInt() and 0xFF) shl 8)
}

fun readInt(data: ByteArray, offset: Int): Long {
    return ((data[offset].toLong() and 0xFF) or
           ((data[offset + 1].toLong() and 0xFF) shl 8) or
           ((data[offset + 2].toLong() and 0xFF) shl 16) or
           ((data[offset + 3].toLong() and 0xFF) shl 24)) and 0xFFFFFFFFL
}