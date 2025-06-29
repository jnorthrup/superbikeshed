package borg.fiduciary.compression

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual class FilePath actual constructor(private val pathString: String) {
    private val nioPath: Path = Paths.get(pathString)

    actual fun pathAsString(): String = pathString
    actual fun name(): String = nioPath.fileName.toString()
    actual fun parent(): FilePath? = nioPath.parent?.let { FilePath(it.toString()) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilePath) return false
        return nioPath == other.nioPath
    }

    override fun hashCode(): Int {
        return nioPath.hashCode()
    }

    override fun toString(): String {
        return nioPath.toString()
    }
}

actual fun FilePath.exists(): Boolean = Files.exists(Paths.get(this.pathAsString()))
actual fun FilePath.isDirectory(): Boolean = Files.isDirectory(Paths.get(this.pathAsString()))
actual fun FilePath.isFile(): Boolean = Files.isRegularFile(Paths.get(this.pathAsString()))

actual fun FilePath.createDirectories() {
    val path = Paths.get(this.pathAsString())
    if (!Files.exists(path)) {
        Files.createDirectories(path)
    }
}

actual fun FilePath.deleteRecursively() {
    val path = Paths.get(this.pathAsString())
    if (Files.exists(path)) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete)
    }
}

actual fun FilePath.resolve(other: String): FilePath {
    return FilePath(Paths.get(this.pathAsString()).resolve(other).toString())
}

actual fun FilePath.resolveSibling(other: String): FilePath {
    return FilePath(Paths.get(this.pathAsString()).resolveSibling(other).toString())
}

actual fun String.toFilePath(): FilePath = FilePath(this)
