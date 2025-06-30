#!/usr/bin/env kscript

package gradle2deps

import java.io.File
import kotlin.system.exitProcess

if (args.size == 0) {
    System.err.println("Usage: gradle2deps.kts <gradle_build_file>")
    exitProcess(-1)
}

val gradleFileName = args[0]
//val gradleFile = File("/Users/brandl/projects/kotlin/krangl/examples/smile/build.gradle")
val gradleFile = File(gradleFileName)

val gradle = gradleFile.readLines()

//val urlRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
//val mavenRepoRegex = """maven [{]{1} url ['"]{1}($urlRegex)['"]{1} [}]""".toRegex()


//mavenRepoRegex.matches("""maven { url 'https://jitpack.io' } """)?.groupValues
//val df = mavenRepoRegex.toRegex().find("""https://jitpack.io""")
//df?.groupValues

// find repos
val reposAnnotatoins = gradle
    .dropWhile { !it.startsWith("repositories") }
    .takeWhile { it != "}" }.toList()
    .map(String::trim)
    .flatMap { it.replace("'", "").split(" ").filter { it.startsWith("http") } }
    .mapIndexed { index, repoUrl -> """@file:MavenRepository("repo${index + 1}","$repoUrl")""" }

// Examples
//    mavenLocal()
//    mavenCentral()
//    maven { url 'https://jitpack.io' }


//  compile "org.jetbrains.kotlin:kotlin-stdlib"
//    compile "org.jetbrains.kotlin:kotlin-reflect"
////    compile "org.jetbrains.kotlin:kotlin-reflect:$kotlin_version"
//
//
//    compile "org.apache.commons:commons-csv:1.3"
////    compile "com.univocity:univocity-parsers:2.7.5"
//    compile 'com.beust:klaxon:0.30'
////    compile 'me.tongfei:progressbar:0.5.5'
//
//    testCompile group: 'junit', name: 'junit', version: '4.12'
////    testCompile "io.kotlintest:kotlintest-runner-junit5:3.1.9"
//    testCompile 'io.kotlintest:kotlintest-assertions:3.1.6'
listOf(1, 2, 3).map(Int::dec)
val depAnnotations = gradle
    .dropWhile { !it.startsWith("dependencies") }
    .takeWhile { it != "}" }
    .map(String::trim)
    .filter { it.startsWith("compile") }
    .map {
        it.removePrefix("compile ")
            .replace("group: '", "\"")
            .replace(", name: '", ":")
            .replace(", version: '", ":")
            .replace("'", "").replace("\"", "")
    }
    .filterNot { it.contains("jetrains.kotlin:kotlin") }
    .map { """@file:DependsOn("$it")""" }


println(reposAnnotatoins.joinToString("\n"))
println()
println()
println(depAnnotations.joinToString("\n"))











