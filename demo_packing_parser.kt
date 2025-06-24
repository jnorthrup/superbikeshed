import borg.trikeshed.lib.PackingParserDemo

fun main() {
    println("=== TrikeShed Register Packing Parser Demo ===\n")
    
    // Demo 1: Basic bash brace expansion
    val demo1 = "{hello,world}"
    println("Demo 1: Basic Braces")
    println(PackingParserDemo.demonstratePacking(demo1))
    println("\n" + "=".repeat(70) + "\n")
    
    // Demo 2: Nested structures
    val demo2 = "{start,{a,b},end}"
    println("Demo 2: Nested Braces")
    println(PackingParserDemo.demonstratePacking(demo2))
    println("\n" + "=".repeat(70) + "\n")
    
    // Demo 3: Sequence patterns
    val demo3 = "file{1..3}.txt"
    println("Demo 3: Sequence Patterns")
    println(PackingParserDemo.demonstratePacking(demo3))
    println("\n" + "=".repeat(70) + "\n")
    
    // Demo 4: Complex mixed patterns
    val demo4 = "test{a,b,{x,y}}.{conf,json}"
    println("Demo 4: Complex Mixed")
    println(PackingParserDemo.demonstratePacking(demo4))
}