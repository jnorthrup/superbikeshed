#!/usr/bin/env kscript
// Demonstrates Java interoperability in k2script

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.io.FileWriter

fun main() {
    // 1. Using Java time API
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    println("Today is: ${today.format(formatter)}")
    
    // 2. Working with Java collections
    val numbers = listOf(3, 1, 4, 1, 5, 9, 2, 6)
    val synchronizedList = Collections.synchronizedList(numbers.toMutableList())
    println("Synchronized list: $synchronizedList")
    
    // 3. Exception handling
    try {
        val parsedDate = LocalDate.parse("2025-13-32")
    } catch (e: Exception) {
        println("Caught exception: ${e.message}")
    }
    
    // 4. File I/O operations
    val tempFile = createTempFile("k2demo", ".txt")
    FileWriter(tempFile).use { writer ->
        writer.write("K2Script Java Interop Demonstration\n")
        writer.write("Generated at: ${LocalDate.now()}")
    }
    println("Created temp file: ${tempFile.absolutePath}")
}

main()