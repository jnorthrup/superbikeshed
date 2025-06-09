#!/usr/bin/env k2script

// Data processing with TrikeShed patterns

println("=== Data Processing Demo ===", AnsiColor.CYAN)

// Simulate some CSV-like data
val rawData = listOf(
    "name,age,city",
    "Alice,25,NYC", 
    "Bob,30,SF",
    "Charlie,35,LA",
    "Diana,28,Boston"
)

println("Raw data:")
rawData.forEach { println("  $it") }

// Convert to Series for TrikeShed processing
val dataLines = Series.of(*rawData.drop(1).toTypedArray()) // Skip header

// Parse with α transformations
data class Person(val name: String, val age: Int, val city: String)

val people = dataLines.α { line ->
    val parts = line.split(",")
    Person(parts[0], parts[1].toInt(), parts[2])
}

println("\n=== Parsed People ===")
people.▶.forEach { person ->
    println("${person.name} (${person.age}) from ${person.city}")
}

// Data transformations with Series
val adults = people.α { it.age >= 30 }.▶.filter { it.age >= 30 }
println("\nAdults (30+): ${adults.map { it.name }}")

val cities = people.α { it.city }.▶.distinct()
println("Cities: ${cities.joinToString(", ")}")

// Join operations for data relationships
val cityStats = cities.map { city ->
    val count = people.▶.count { it.city == city }
    city j count
}

println("\n=== City Statistics ===")
cityStats.forEach { stat ->
    println("${stat.first}: ${stat.second} people")
}

// Aggregate with Series processing
val ages = people.α { it.age }
val totalAge = ages.▶.sum()
val avgAge = totalAge.toDouble() / ages.size

println("\nAge statistics:")
println("  Total: $totalAge")
println("  Average: ${"%.1f".format(avgAge)}")
println("  Count: ${ages.size}")

// Memory-efficient processing
Memory.withCleanup {
    val processedData = people
        .α { "${it.name}:${it.city}" }
        .α { it.uppercase() }
    
    println("\nProcessed data:")
    processedData.▶.forEach { println("  $it") }
}

println("\nData processing complete! 📊", AnsiColor.GREEN)