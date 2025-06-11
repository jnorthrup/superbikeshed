package borg.trikeshed.io

import borg.trikeshed.lib.* // This should cover Series and toSeries
import kotlin.jvm.*

import simple.PosixFile

actual fun readLines(filename: String): Series<String> = simple.PosixFile.readLines(filename).toSeries()
