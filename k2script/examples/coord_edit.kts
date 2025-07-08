#!/usr/bin/env k2script

// Coordinate-based editing using grep -nC3 pattern
// User's insight: grep -nC3 creates edit coordinates with line numbers and target-center

@file:Import("k2script.trikeshed.*")

import java.io.File

fun coordinateEdit(file: File, searchTerm: String): Series<String> {
    val process = ProcessBuilder("grep", "-nC3", searchTerm, file.absolutePath)
        .redirectErrorStream(true)
        .start()
    
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor()
    
    val lines = output.split('\n').filter { it.isNotBlank() }.toTypedArray()
    return Series(lines)
}

// Example usage
val targetFile = File("src/main/kotlin/k2script/K2script.kt")
if (targetFile.exists()) {
    val coordinates = coordinateEdit(targetFile, "executeScript")
    println("Edit coordinates for 'executeScript':")
    coordinates.play.forEach { println(it) }
}