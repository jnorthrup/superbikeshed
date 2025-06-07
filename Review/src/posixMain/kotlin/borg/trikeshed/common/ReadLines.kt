package borg.trikeshed.io

import borg.trikeshed.lib.*
import kotlin.jvm.*

import simple.PosixFile

actual fun readLines(filename: String): List<String> = simple.PosixFile.readLines(filename)
