package borg.trikeshed.io

actual fun readLinesSeq(path: String): Sequence<String> {
    println("readLinesSeq not implemented for macosArm64")
    return emptySequence()
}

actual fun readLines(path: String): List<String> {
    println("readLines not implemented for macosArm64")
    return emptyList()
}