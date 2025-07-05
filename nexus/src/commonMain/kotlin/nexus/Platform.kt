package nexus

expect fun getEnvironmentVariable(name: String): String?
expect fun getCurrentTime(): String
expect fun joinPath(base: String, path: String): String
expect class File(path: String) {
    fun exists(): Boolean
    fun isDirectory(): Boolean
    fun listFiles(): List<String>?
    fun readText(): String
    fun writeText(text: String)
}
