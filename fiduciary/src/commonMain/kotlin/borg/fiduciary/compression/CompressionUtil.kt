package borg.fiduciary.compression

// This file can be used for any shared constants or helper functions
// related to compression in the future.

// Buffer size for streaming operations, can be tuned.
internal const val DEFAULT_BUFFER_SIZE = 8192
internal const val ZIP_ENTRY_BUFFER_SIZE = 4096 // Potentially smaller for numerous small zip entries if needed

/**
 * A platform-agnostic representation of a file path.
 *
 * This class provides a common way to refer to file system paths across different platforms.
 * Actual implementations will delegate to platform-specific path handling mechanisms
 * (e.g., `java.nio.file.Path` on JVM).
 *
 * @property path The string representation of the path.
 */
expect class FilePath {
    /**
     * Constructs a FilePath instance.
     * @param path The string representation of the path.
     */
    constructor(path: String)

    /**
     * Returns the string representation of this path.
     */
    fun pathAsString(): String

    /**
     * Returns the name of the file or directory denoted by this path.
     * This is the last component of the path.
     */
    fun name(): String

    /**
     * Returns the parent path, or null if this path does not have a parent.
     */
    fun parent(): FilePath?
}

/**
 * Checks if the file or directory denoted by this path exists.
 * @return `true` if the file or directory exists, `false` otherwise.
 */
expect fun FilePath.exists(): Boolean

/**
 * Checks if the path denotes a directory.
 * @return `true` if the path is a directory, `false` otherwise.
 */
expect fun FilePath.isDirectory(): Boolean

/**
 * Checks if the path denotes a regular file.
 * @return `true` if the path is a regular file, `false` otherwise.
 */
expect fun FilePath.isFile(): Boolean

/**
 * Creates the directory named by this path, including any necessary but nonexistent parent directories.
 * Does nothing if the directory already exists.
 */
expect fun FilePath.createDirectories()

/**
 * Deletes the file or directory denoted by this path.
 * If the path is a directory, it is deleted recursively.
 * If the path does not exist, this function does nothing.
 * **Caution**: This operation is destructive and irreversible.
 */
expect fun FilePath.deleteRecursively()

/**
 * Resolves the given [other] path against this path.
 * If [other] is an absolute path then this function is equivalent to calling `toFilePath(other)`.
 * If [other] is a relative path then this function resolves it against this path.
 *
 * @param other The path string to resolve against this path.
 * @return The resulting path.
 */
expect fun FilePath.resolve(other: String): FilePath

/**
 * Resolves the given [other] path against this path's parent directory.
 * This is useful for creating a path to a sibling file or directory.
 * If this path has no parent, it resolves [other] as if it were an absolute path.
 *
 * @param other The path string to resolve.
 * @return The resulting sibling path.
 */
expect fun FilePath.resolveSibling(other: String): FilePath

/**
 * Converts a string to a [FilePath].
 * @return The [FilePath] instance.
 */
expect fun String.toFilePath(): FilePath
