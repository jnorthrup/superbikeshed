import java.net.URL
import java.net.HttpURLConnection

fun main() {
    val url = "https://archive.org/download/patrickdevinefiles/Patrick%20Devine%20files.zip"
    
    // Get file size first
    val sizeConn = URL(url).openConnection() as HttpURLConnection
    sizeConn.requestMethod = "HEAD"
    sizeConn.connect()
    val fileSize = sizeConn.getHeaderField("Content-Length")?.toLong() ?: 0
    sizeConn.disconnect()
    println("File size: $fileSize")
    
    // Step 1: Find End of Central Directory Record (EOCD)
    // EOCD is at least 22 bytes, search in last 64KB
    val searchSize = minOf(65536, fileSize)
    val rangeStart = fileSize - searchSize
    
    val eocdConn = URL(url).openConnection() as HttpURLConnection
    eocdConn.setRequestProperty("Range", "bytes=$rangeStart-")
    val eocdData = eocdConn.inputStream.readBytes()
    eocdConn.disconnect()
    
    // Find EOCD signature (0x06054b50)
    var eocdOffset = -1
    for (i in eocdData.size - 22 downTo 0) {
        if (eocdData[i] == 0x50.toByte() && 
            eocdData[i+1] == 0x4B.toByte() && 
            eocdData[i+2] == 0x05.toByte() && 
            eocdData[i+3] == 0x06.toByte()) {
            eocdOffset = i
            break
        }
    }
    
    if (eocdOffset == -1) {
        println("EOCD not found")
        return
    }
    
    // Parse EOCD
    val centralDirSize = readInt(eocdData, eocdOffset + 12)
    var centralDirOffset = readInt(eocdData, eocdOffset + 16).toLong() and 0xFFFFFFFFL
    
    println("Central directory size: $centralDirSize")
    println("Central directory offset: $centralDirOffset")
    
    // Check for ZIP64
    if (centralDirOffset == 0xFFFFFFFFL) {
        // Look for ZIP64 end of central directory locator
        val zip64LocOffset = eocdOffset - 20
        if (zip64LocOffset >= 0 && 
            eocdData[zip64LocOffset] == 0x50.toByte() &&
            eocdData[zip64LocOffset+1] == 0x4B.toByte() &&
            eocdData[zip64LocOffset+2] == 0x06.toByte() &&
            eocdData[zip64LocOffset+3] == 0x07.toByte()) {
            
            // Get ZIP64 EOCD offset
            val zip64EocdOffset = readLong(eocdData, zip64LocOffset + 8)
            println("ZIP64 detected, EOCD at: $zip64EocdOffset")
            
            // Fetch ZIP64 EOCD
            val zip64Conn = URL(url).openConnection() as HttpURLConnection
            zip64Conn.setRequestProperty("Range", "bytes=$zip64EocdOffset-${zip64EocdOffset + 56}")
            val zip64Data = zip64Conn.inputStream.readBytes()
            zip64Conn.disconnect()
            
            // Get real central directory offset from ZIP64 EOCD
            centralDirOffset = readLong(zip64Data, 48)
            println("ZIP64 central directory offset: $centralDirOffset")
        }
    }
    
    // Fetch central directory
    val cdConn = URL(url).openConnection() as HttpURLConnection
    cdConn.setRequestProperty("Range", "bytes=$centralDirOffset-${centralDirOffset + centralDirSize}")
    val cdData = cdConn.inputStream.readBytes()
    cdConn.disconnect()
    
    println("\nSearching central directory for files containing '0720'...")
    
    // Parse central directory entries
    var offset = 0
    var found = 0
    
    while (offset < cdData.size - 46) {
        // Check for central directory file header signature (0x02014b50)
        if (cdData[offset] == 0x50.toByte() &&
            cdData[offset+1] == 0x4B.toByte() &&
            cdData[offset+2] == 0x01.toByte() &&
            cdData[offset+3] == 0x02.toByte()) {
            
            val compressedSize = readInt(cdData, offset + 20)
            val uncompressedSize = readInt(cdData, offset + 24)
            val nameLen = readShort(cdData, offset + 28)
            val extraLen = readShort(cdData, offset + 30)
            val commentLen = readShort(cdData, offset + 32)
            val localHeaderOffset = readInt(cdData, offset + 42).toLong() and 0xFFFFFFFFL
            
            if (offset + 46 + nameLen <= cdData.size) {
                val filename = String(cdData, offset + 46, nameLen)
                if (filename.contains("0720")) {
                    found++
                    println("FOUND: $filename")
                    println("  Compressed: $compressedSize bytes")
                    println("  Uncompressed: $uncompressedSize bytes")
                    println("  Local header at: $localHeaderOffset")
                }
            }
            
            offset += 46 + nameLen + extraLen + commentLen
        } else {
            offset++
        }
    }
    
    println("\nTotal files containing '0720': $found")
}

fun readShort(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or 
           ((data[offset + 1].toInt() and 0xFF) shl 8)
}

fun readInt(data: ByteArray, offset: Int): Int {
    return (data[offset].toInt() and 0xFF) or
           ((data[offset + 1].toInt() and 0xFF) shl 8) or
           ((data[offset + 2].toInt() and 0xFF) shl 16) or
           ((data[offset + 3].toInt() and 0xFF) shl 24)
}

fun readLong(data: ByteArray, offset: Int): Long {
    return (data[offset].toLong() and 0xFF) or
           ((data[offset + 1].toLong() and 0xFF) shl 8) or
           ((data[offset + 2].toLong() and 0xFF) shl 16) or
           ((data[offset + 3].toLong() and 0xFF) shl 24) or
           ((data[offset + 4].toLong() and 0xFF) shl 32) or
           ((data[offset + 5].toLong() and 0xFF) shl 40) or
           ((data[offset + 6].toLong() and 0xFF) shl 48) or
           ((data[offset + 7].toLong() and 0xFF) shl 56)
}