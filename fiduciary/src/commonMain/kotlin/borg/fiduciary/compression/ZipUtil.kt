package borg.fiduciary.compression

import java.io.InputStream
import java.io.OutputStream

/**
 * Represents information about an entry within a Zip archive.
 *
 * @property name The name of the zip entry (full path within the archive).
 * @property isDirectory True if the entry represents a directory, false otherwise.
 * @property compressedSize The size of the compressed data in bytes.
 * @property uncompressedSize The size of the uncompressed data in bytes.
 */
data class ZipEntryInfo(
    val name: String,
    val isDirectory: Boolean,
    val compressedSize: Long,
    val uncompressedSize: Long
)

/**
 * Utility object for Zip archive manipulation.
 *
 * This object provides methods for creating, extracting, and listing entries in Zip archives.
 * Implementations are expected to handle large archives and files efficiently,
 * leveraging streaming where possible. For operations like listing entries or extracting
 * specific entries, implementations should ideally use Zip's central directory for
 * optimized access, especially for large archives.
 *
 * Note on large remote files: If dealing with Zip archives that are too large to download
 * (e.g., hosted on a server), the `archiveSource` [InputStream] might not be sufficient
 * for efficient random access. True random access would require range requests if the
 * source is remote and supports them, or a seekable file channel if local. The current
 * [InputStream]-based API implies sequential access for listing and extraction if not
 * backed by a seekable source.
 */
expect object ZipUtil {
    /**
     * Compresses multiple files or data streams into a Zip archive.
     *
     * Each entry in the `filesToCompress` map will be added to the Zip archive.
     * The key of the map is the name of the entry (including any path structure),
     * and the value is an [InputStream] providing the data for that entry.
     *
     * @param filesToCompress A map where keys are entry names (e.g., "path/to/file.txt")
     *                        and values are [InputStream]s for the content of each entry.
     *                        The provided [InputStream]s will be read to completion and closed by this method.
     * @param destination The [OutputStream] to write the generated Zip archive to. This stream will be
     *                    written to but not closed by this method; the caller is responsible for closing it.
     * @throws java.io.IOException if an I/O error occurs during reading sources or writing the archive.
     */
    fun compressFiles(filesToCompress: Map<String, InputStream>, destination: OutputStream)

    /**
     * Decompresses all entries from a Zip archive to a specified output directory.
     *
     * The directory structure within the Zip archive will be recreated under `outputDirectoryPath`.
     * If `outputDirectoryPath` does not exist, it will be created.
     *
     * @param archiveSource The [InputStream] for the Zip archive. This stream will be read to completion
     *                      and closed by this method.
     * @param outputDirectoryPath The [FilePath] representing the target directory where files will be extracted.
     * @throws java.io.IOException if an I/O error occurs, if `outputDirectoryPath` is not a directory or cannot be created,
     *                             or if an entry attempts to write outside the target directory (Zip Slip vulnerability).
     */
    fun decompressAll(archiveSource: InputStream, outputDirectoryPath: FilePath)

    /**
     * Extracts a single specified entry from a Zip archive to the destination [OutputStream].
     *
     * If the entry is found and is a file, its content will be written to the `destination` stream.
     * If the entry is a directory or not found, the method will return `false` and nothing will be written.
     *
     * @param archiveSource The [InputStream] for the Zip archive. This stream will be read (partially or fully)
     *                      and closed by this method.
     * @param entryName The full name/path of the entry to extract from the Zip archive (e.g., "path/to/file.txt").
     * @param destination The [OutputStream] to write the extracted entry's content to. This stream will be
     *                    written to but not closed by this method.
     * @return `true` if the entry was found and successfully extracted, `false` otherwise (e.g., entry not found, or entry is a directory).
     * @throws java.io.IOException if an I/O error occurs during reading the archive or writing the entry.
     */
    fun extractEntry(archiveSource: InputStream, entryName: String, destination: OutputStream): Boolean

    /**
     * Lists all entries in a Zip archive, providing metadata for each.
     *
     * This operation iterates through the Zip archive's entries. For optimal performance
     * on large, seekable archives, implementations should use the central directory.
     * If the `archiveSource` is a non-seekable stream, this method will consume the stream.
     *
     * @param archiveSource The [InputStream] for the Zip archive. This stream will be read
     *                      (potentially to completion if not seekable) and closed by this method.
     * @return A list of [ZipEntryInfo] objects, each describing an entry in the archive.
     * @throws java.io.IOException if an I/O error occurs while reading the archive.
     */
    fun listEntries(archiveSource: InputStream): List<ZipEntryInfo>
}
