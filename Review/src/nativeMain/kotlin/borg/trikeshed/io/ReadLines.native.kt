package borg.trikeshed.io

actual fun readLinesSeq(path: String): Sequence<String> {
    println("readLinesSeq $path (Native placeholder)")
    return sequenceOf("Mock line 1", "Mock line 2")
}

actual fun readLines(path: String): List<String> {
    println("readLines $path (Native placeholder)")
    return listOf("Mock line 1", "Mock line 2")
}
