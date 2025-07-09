import java.net.URL
import java.net.HttpURLConnection

fun main() {
    val url = "https://archive.org/download/patrickdevinecalls/Patrick%20Devine%20Calls.zip"
    println("Getting file size...")
    
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.requestMethod = "HEAD"
    val size = conn.getHeaderField("Content-Length")?.toLong() ?: 0
    conn.disconnect()
    
    println("Archive size: $size bytes")
    
    // Get end of central directory
    val endConn = URL(url).openConnection() as HttpURLConnection
    endConn.setRequestProperty("Range", "bytes=${size - 22}-")
    
    val endData = endConn.inputStream.readBytes()
    endConn.disconnect()
    
    // Find central directory offset
    var centralDirOffset = 0L
    if (endData[0] == 0x50.toByte() && endData[1] == 0x4B.toByte()) {
        centralDirOffset = (endData[16].toLong() and 0xFF) or
                          ((endData[17].toLong() and 0xFF) shl 8) or
                          ((endData[18].toLong() and 0xFF) shl 16) or
                          ((endData[19].toLong() and 0xFF) shl 24)
    }
    
    println("Central directory at: $centralDirOffset")
    
    // Get central directory
    val cdConn = URL(url).openConnection() as HttpURLConnection
    cdConn.setRequestProperty("Range", "bytes=$centralDirOffset-")
    
    val cdData = cdConn.inputStream.readBytes()
    cdConn.disconnect()
    
    println("Got ${cdData.size} bytes of central directory")
    
    // Search for 0720
    var i = 0
    while (i < cdData.size - 46) {
        if (cdData[i] == 0x50.toByte() && cdData[i+1] == 0x4B.toByte() &&
            cdData[i+2] == 0x01.toByte() && cdData[i+3] == 0x02.toByte()) {
            
            val nameLen = (cdData[i+28].toInt() and 0xFF) or ((cdData[i+29].toInt() and 0xFF) shl 8)
            if (i + 46 + nameLen <= cdData.size) {
                val name = String(cdData, i + 46, nameLen)
                if (name.contains("0720")) {
                    println("FOUND: $name")
                }
            }
            i += 46 + nameLen + 
                ((cdData[i+30].toInt() and 0xFF) or ((cdData[i+31].toInt() and 0xFF) shl 8)) +
                ((cdData[i+32].toInt() and 0xFF) or ((cdData[i+33].toInt() and 0xFF) shl 8))
        } else {
            i++
        }
    }
}