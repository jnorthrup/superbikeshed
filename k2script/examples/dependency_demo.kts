#!/usr/bin/env kscript
// Demonstrates dependency management in k2script
@file:DependsOn("com.google.code.gson:gson:2.10.1")

import com.google.gson.Gson

data class Person(val name: String, val age: Int)

fun main() {
    val gson = Gson()
    val person = Person("Alice", 30)
    
    // Convert to JSON
    val json = gson.toJson(person)
    println("JSON: $json")
    
    // Parse from JSON
    val parsed = gson.fromJson("""{"name":"Bob","age":25}""", Person::class.java)
    println("Parsed: $parsed")
}

main()