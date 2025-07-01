package borg.trikeshed.io

import simple.PosixFile

/** lean on getline to read a file into a sequence of CharIndexed */
actual fun readLinesSeq(path: String): Sequence<String>  = PosixFile.readLinesSeq(path)


/** lean on getline to read a file into a List of CharIndexed */
actual fun readLines(path: String): List<String> = PosixFile.readLines(path)
