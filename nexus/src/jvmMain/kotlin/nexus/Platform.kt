package nexus

actual fun getEnvironmentVariable(name: String): String? {
    return System.getenv(name)
}

actual fun getCurrentTime(): String {
    return java.time.LocalDateTime.now().toString()
}

actual class File actual constructor(path: String) {
    private val jvmFile = java.io.File(path)

    actual fun exists(): Boolean = jvmFile.exists()
    actual fun isDirectory(): Boolean = jvmFile.isDirectory
    actual fun listFiles(): List<String>? = jvmFile.listFiles()?.map { it.name }?.toList()
    actual fun readText(): String = jvmFile.readText()
    actual fun writeText(text: String) = jvmFile.writeText(text)
}
