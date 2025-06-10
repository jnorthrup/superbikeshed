import java.io.File

fun main() {
    val kotlinDir = File("ta4k-spacegraph-moneyfan-demo/src/jsMain/kotlin")
    kotlinDir.walk()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { file ->
            val content = file.readText()
            val updated = content.replace("com.example.demo", "com.moneyfan.demo")
            file.writeText(updated)
        }
}