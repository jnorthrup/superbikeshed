import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.Inflater

fun main() {
    // Patrick Devine archives
    val archives = listOf(
        "https://archive.org/download/patrickdevine/patrickdevine.zip",
        "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    )

    println("🎯 CONQUERING 0720.txt")
    println("=" + "=".repeat(49))

    // For each archive, get central directory and find 0720.txt
    archives.forEach { archiveUrl ->
    println("\n📦 Checking: ${archiveUrl.substringAfterLast('/')}")
    
    try {
        // Get file size
        val sizeConnection = URL(archiveUrl).openConnection() as HttpURLConnection
        sizeConnection.requestMethod = "HEAD"
        val fileSize = sizeConnection.getHeaderField("Content-Length")?.toLong() ?: 0L
        sizeConnection.disconnect()
        println("   Size: ${fileSize / 1024 / 1024} MB")
        
        // Get last 256KB for central directory
        val rangeStart = maxOf(0, fileSize - 256 * 1024)
        val connection = URL(archiveUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", "bytes=$rangeStart-")
        connection.setRequestProperty("User-Agent", "0720Hunter/1.0")
        
        if (connection.responseCode == 206) {
            val centralDirData = connection.inputStream.readBytes()
            connection.disconnect()
            
            println("   Got ${centralDirData.size} bytes of central directory")
            
            // Search for 0720 in filenames
            val found0720 = search0720InCentralDir(centralDirData, fileSize - 256 * 1024)
            
            if (found0720 != null) {
                println("\n🎉 FOUND 0720.txt!")
                println("   Offset: ${found0720.offset}")
                println("   Size: ${found0720.size} bytes")
                
                // Extract the file
                extract0720(archiveUrl, found0720)
                return
            }
        }
    } catch (e: Exception) {
        println("   Error: ${e.message}")
    }
    println("\n❌ 0720.txt not found in either archive")
}
}

data class FileEntry(val filename: String, val offset: Long, val size: Long, val method: Int)

fun search0720InCentralDir(data: ByteArray, rangeOffset: Long): FileEntry? {
    var i = 0
    while (i < data.size - 46) {
        // Look for central directory file header signature (0x02014b50)
        if (data[i] == 0x50.toByte() && data[i+1] == 0x4b.toByte() && 
            data[i+2] == 0x01.toByte() && data[i+3] == 0x02.toByte()) {
            
            // Parse the header
            val method = readShort(data, i + 10)
            val compressedSize = readInt(data, i + 20)
            val uncompressedSize = readInt(data, i + 24)
            val nameLength = readShort(data, i + 28)
            val extraLength = readShort(data, i + 30)
            val localHeaderOffset = readInt(data, i + 42)
            
            // Get filename
            if (i + 46 + nameLength <= data.size) {
                val filename = String(data, i + 46, nameLength)
                
                if (filename.contains("0720")) {
                    println("   Found: $filename")
                    return FileEntry(filename, localHeaderOffset.toLong(), compressedSize.toLong(), method)
                }
            }
            
            // Move to next entry
            i += 46 + nameLength + extraLength + readShort(data, i + 32)
        } else {
            i++
        }
    }
    return null
}

fun extract0720(archiveUrl: String, entry: FileEntry) {
    println("\n📥 Extracting ${entry.filename}...")
    
    // Fetch the file data
    val connection = URL(archiveUrl).openConnection() as HttpURLConnection
    val rangeEnd = entry.offset + 1024 + entry.size // Get header + data
    connection.setRequestProperty("Range", "bytes=${entry.offset}-$rangeEnd")
    connection.setRequestProperty("User-Agent", "0720Extractor/1.0")
    
    if (connection.responseCode == 206) {
        val data = connection.inputStream.readBytes()
        connection.disconnect()
        
        // Parse local file header
        val nameLen = readShort(data, 26)
        val extraLen = readShort(data, 28)
        val dataOffset = 30 + nameLen + extraLen
        
        // Extract compressed data
        val compressedData = data.sliceArray(dataOffset until (dataOffset + entry.size.toInt()))
        
        // Decompress if needed
        val uncompressedData = when (entry.method) {
            0 -> compressedData // Stored
            8 -> inflate(compressedData) // Deflated
            else -> throw Exception("Unsupported method: ${entry.method}")
        }
        
        // Save it!
        val outputFile = File("conquered-0720.txt")
        outputFile.writeBytes(uncompressedData)
        
        println("\n✅ CONQUERED! Saved to: ${outputFile.absolutePath}")
        println("   Size: ${uncompressedData.size} bytes")
        
        // Show preview
        val text = String(uncompressedData)
        println("\n📄 Preview:")
        println("-".repeat(50))
        println(text.take(500))
        if (text.length > 500) println("... (${text.length - 500} more bytes)")
    }
}

fun inflate(compressed: ByteArray): ByteArray {
    val inflater = Inflater()
    inflater.setInput(compressed)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(1024)
    while (!inflater.finished()) {
        val count = inflater.inflate(buffer)
        output.write(buffer, 0, count)
    }
    inflater.end()
    return output.toByteArray()
}

fun readShort(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}

fun readInt(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or
           ((data[offset + 1].toInt() and 0xFF) shl 8) or
           ((data[offset + 2].toInt() and 0xFF) shl 16) or
           ((data[offset + 3].toInt() and 0xFF) shl 24)
}

