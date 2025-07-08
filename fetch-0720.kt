#!/usr/bin/env kotlin

import java.io.File
import java.net.URL

// Just fetch the fucking file
val file = File("patrick0720.txt")
if (!file.exists()) {
    println("ERROR: patrick0720.txt not found")
    System.exit(1)
}

println(file.readText())