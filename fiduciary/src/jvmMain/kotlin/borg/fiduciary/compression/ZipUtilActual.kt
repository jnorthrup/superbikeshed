package borg.fiduciary.compression

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object ZipUtil {
    actual fun compressFiles(filesToCompress: Map<String, InputStream>, destination: OutputStream) {
        ZipOutputStream(destination.buffered(DEFAULT_BUFFER_SIZE)).use { zos ->
            filesToCompress.forEach { (entryName, sourceInputStream) ->
                val zipEntry = ZipEntry(entryName)
                zos.putNextEntry(zipEntry)
                sourceInputStream.buffered(DEFAULT_BUFFER_SIZE).use { bufferedSource ->
                    bufferedSource.copyTo(zos, DEFAULT_BUFFER_SIZE)
                }
                zos.closeEntry()
            }
        }
    }

    actual fun decompressAll(archiveSource: InputStream, outputDirectoryPath: FilePath) {
        val targetDir = Paths.get(outputDirectoryPath.pathAsString())
        if (!outputDirectoryPath.exists()) {
            outputDirectoryPath.createDirectories()
        } else if (!outputDirectoryPath.isDirectory()) {
            throw IOException("Output path exists but is not a directory: $outputDirectoryPath")
        }

        ZipInputStream(archiveSource.buffered(DEFAULT_BUFFER_SIZE)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val entryPath = targetDir.resolve(zipEntry.name)
                // Prevent Zip Slip vulnerability
                if (!entryPath.normalize().startsWith(targetDir.normalize())) {
                    throw IOException("Zip entry is trying to escape the target directory: ${zipEntry.name}")
                }

                if (zipEntry.isDirectory) {
                    FilePath(entryPath.toString()).createDirectories()
                } else {
                    // Ensure parent directory exists
                    entryPath.parent?.let { FilePath(it.toString()).createDirectories() }

                    Files.newOutputStream(entryPath).buffered(DEFAULT_BUFFER_SIZE).use { outputStream ->
                        zis.copyTo(outputStream, DEFAULT_BUFFER_SIZE)
                    }
                }
                zis.closeEntry()
                zipEntry = zis.nextEntry
            }
        }
    }

    actual fun extractEntry(archiveSource: InputStream, entryName: String, destination: OutputStream): Boolean {
        ZipInputStream(archiveSource.buffered(DEFAULT_BUFFER_SIZE)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (zipEntry.name == entryName) {
                    if (zipEntry.isDirectory) {
                        return false // Cannot extract a directory to an OutputStream
                    }
                    destination.buffered(DEFAULT_BUFFER_SIZE).use { bufferedDest ->
                        zis.copyTo(bufferedDest, DEFAULT_BUFFER_SIZE)
                    }
                    return true
                }
                zis.closeEntry() // Important to close entry if not the target, to advance properly
                zipEntry = zis.nextEntry
            }
        }
        return false // Entry not found
    }

    actual fun listEntries(archiveSource: InputStream): List<ZipEntryInfo> {
        val entries = mutableListOf<ZipEntryInfo>()
        ZipInputStream(archiveSource.buffered(DEFAULT_BUFFER_SIZE)).use { zis ->
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                entries.add(
                    ZipEntryInfo(
                        name = zipEntry.name,
                        isDirectory = zipEntry.isDirectory,
                        compressedSize = zipEntry.compressedSize,
                        uncompressedSize = zipEntry.size
                    )
                )
                zipEntry = zis.nextEntry
            }
        }
        return entries
    }
}
