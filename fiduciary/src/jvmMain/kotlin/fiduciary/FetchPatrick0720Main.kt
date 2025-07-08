package fiduciary

import fiduciary.fetch.ZipRangeFetcher
import borg.trikeshed.net.http.*
import kotlinx.coroutines.*
import java.io.File

suspend fun main() {
    val file = File("patrick0720.txt")
    
    if (file.exists()) {
        println("Found patrick0720.txt")
        println("Size: ${file.length()} bytes")
        println("Content:")
        println(file.readText())
    } else {
        println("patrick0720.txt not found, fetching from Patrick Divine archives...")
        
        val ioContext = IOContext.NioContext("patrick-fetcher")
        val httpClient = HttpClientBuilder()
            .ioContext(ioContext)
            .build()
        
        val fetcher = ZipRangeFetcher(httpClient)
        val centralDirs = fetcher.fetchZipCentralDirs()
        
        // Look for patrick0720.txt in the archives
        centralDirs.forEach { centralDir ->
            centralDir.entries.forEach { entry ->
                if (entry.filename.contains("0720") || entry.filename.contains("patrick0720")) {
                    println("Found: ${entry.filename} in ${centralDir.archiveName}")
                    // TODO: Extract the actual file content using range requests
                }
            }
        }
    }
}