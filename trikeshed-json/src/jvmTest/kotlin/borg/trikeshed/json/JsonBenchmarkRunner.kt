@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.json

import kotlin.system.*
import kotlin.time.*

/**
 * JSON Benchmark Runner - Executes all microbenchmarks and generates reports
 */
object JsonBenchmarkRunner {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("🚀 JSON Parser Microbenchmark Competition Rematch")
        println("=".repeat(60))
        
        val startTime = System.currentTimeMillis()
        
        // Parse command line arguments
        val config = parseArgs(args)
        
        // Run benchmarks based on configuration
        when {
            config.runAll -> runAllBenchmarks()
            config.runQuick -> runQuickBenchmarks()
            config.runSpecific.isNotEmpty() -> runSpecificBenchmarks(config.runSpecific)
            else -> runDefaultBenchmarks()
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        println("\n" + "=".repeat(60))
        println("🏁 BENCHMARK COMPLETION SUMMARY")
        println("=".repeat(60))
        println("Total duration: ${duration}ms")
        println("Timestamp: ${System.currentTimeMillis()}")
        println("Platform: JVM ${System.getProperty("java.version")}")
        println("Architecture: ${System.getProperty("os.arch")}")
        println("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
        
        if (config.generateReport) {
            generateDetailedReport()
        }
    }
    
    private fun runAllBenchmarks() {
        println("🔥 Running ALL microbenchmarks...")
        
        // Core benchmarks
        runBenchmark("ComprehensiveJsonBenchmark")
        
        // Individual benchmark suites
        runBenchmark("KotlinBenchmark")
        runBenchmark("BigJsonBenchmark")
        runBenchmark("VectorApiBenchmark")
        runBenchmark("SimpleBenchmarkTest")
        runBenchmark("JsonBenchmark")
        
        // JMH-style benchmarks
        runBenchmark("JmhBenchmark")
        runBenchmark("ManualBenchmark")
        
        // Integration benchmarks
        runIntegrationBenchmarks()
    }
    
    private fun runQuickBenchmarks() {
        println("⚡ Running QUICK microbenchmarks...")
        
        // Essential benchmarks only
        runBenchmark("SimpleBenchmarkTest")
        runBenchmark("KotlinBenchmark")
    }
    
    private fun runSpecificBenchmarks(benchmarks: List<String>) {
        println("🎯 Running SPECIFIC microbenchmarks: ${benchmarks.joinToString(", ")}")
        
        benchmarks.forEach { benchmark ->
            runBenchmark(benchmark)
        }
    }
    
    private fun runDefaultBenchmarks() {
        println("📊 Running DEFAULT microbenchmarks...")
        
        // Default set of most important benchmarks
        runBenchmark("ComprehensiveJsonBenchmark")
        runBenchmark("KotlinBenchmark")
        runBenchmark("BigJsonBenchmark")
    }
    
    private fun runBenchmark(benchmarkName: String) {
        println("\n" + "-".repeat(50))
        println("Running: $benchmarkName")
        println("-".repeat(50))
        
        try {
            when (benchmarkName) {
                "ComprehensiveJsonBenchmark" -> {
                    val benchmark = ComprehensiveJsonBenchmark()
                    benchmark.runComprehensiveBenchmarks()
                }
                "KotlinBenchmark" -> {
                    val benchmark = KotlinBenchmark()
                    benchmark.runAllBenchmarks()
                }
                "BigJsonBenchmark" -> {
                    val benchmark = BigJsonBenchmark()
                    benchmark.benchmarkBigJson()
                }
                "VectorApiBenchmark" -> {
                    val benchmark = VectorApiBenchmark()
                    benchmark.runAllBenchmarks()
                }
                "SimpleBenchmarkTest" -> {
                    val benchmark = SimpleBenchmarkTest()
                    benchmark.benchmarkSimpleVsBitmap()
                    benchmark.benchmarkPropertyExtraction()
                    benchmark.benchmarkRepeatedQueries()
                    benchmark.benchmarkFingerprinting()
                    benchmark.benchmarkLargeJson()
                }
                "JsonBenchmark" -> {
                    val benchmark = JsonBenchmark()
                    benchmark.benchmarkJsonBbcursiveParse()
                    benchmark.benchmarkNaturalBBCursiveParseJson()
                }
                "JmhBenchmark" -> {
                    JmhBenchmark.runPropertyExtractionBenchmark()
                    JmhBenchmark.runParsingBenchmark()
                    JmhBenchmark.runBigJsonBenchmark()
                }
                "ManualBenchmark" -> {
                    ManualBenchmark.runAllBenchmarks()
                }
                else -> {
                    println("❌ Unknown benchmark: $benchmarkName")
                }
            }
        } catch (e: Exception) {
            println("❌ Error running $benchmarkName: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun runIntegrationBenchmarks() {
        println("\n" + "-".repeat(50))
        println("Running Integration Benchmarks")
        println("-".repeat(50))
        
        try {
            // Run Python integration tests if available
            val pythonScript = "tests/integration/trikeshed-json/test_json_scanner_benchmarks.py"
            if (java.io.File(pythonScript).exists()) {
                println("🐍 Running Python integration benchmarks...")
                val process = ProcessBuilder("python3", pythonScript)
                    .inheritIO()
                    .start()
                process.waitFor()
            }
            
            // Run Kotlin script benchmarks
            val kotlinScript = "tests/integration/trikeshed-json/benchmark-runner.kts"
            if (java.io.File(kotlinScript).exists()) {
                println("📜 Running Kotlin script benchmarks...")
                val process = ProcessBuilder("kotlin", kotlinScript)
                    .inheritIO()
                    .start()
                process.waitFor()
            }
            
        } catch (e: Exception) {
            println("❌ Error running integration benchmarks: ${e.message}")
        }
    }
    
    private fun generateDetailedReport() {
        println("\n" + "=".repeat(60))
        println("📋 GENERATING DETAILED BENCHMARK REPORT")
        println("=".repeat(60))
        
        val report = buildString {
            appendLine("# JSON Parser Microbenchmark Competition Rematch Report")
            appendLine()
            appendLine("## Test Environment")
            appendLine("- JVM: ${System.getProperty("java.version")}")
            appendLine("- Architecture: ${System.getProperty("os.arch")}")
            appendLine("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            appendLine("- Timestamp: ${System.currentTimeMillis()}")
            appendLine()
            appendLine("## Implementations Tested")
            appendLine("1. SimpleJsonScanner - Basic character-by-character parsing")
            appendLine("2. JsonScannerCompact - Bitmap-based structural indexing")
            appendLine("3. FastJsonScanner - O(1) property lookup with LinkedHashMap")
            appendLine("4. HardwareAcceleratedJsonScanner - Platform-specific acceleration")
            appendLine("5. VectorizedJsonScanner - JVM Vector API SIMD acceleration")
            appendLine("6. MemorySegmentJsonScanner - Memory-mapped parsing")
            appendLine("7. M3NativeJsonScanner - Apple M3 Metal GPU acceleration")
            appendLine("8. SwarmJsonScanner - Multi-threaded swarm processing")
            appendLine("9. CoroutineSwarmJsonScanner - Async swarm processing")
            appendLine("10. JsonBBCursive - BBCursive pattern-based parser")
            appendLine("11. JsonBbcursive - Alternative BBCursive implementation")
            appendLine("12. NaturalBBCursive - Register-at-a-time scanning")
            appendLine("13. TiledBBCursive - 8/16-byte tiled processing")
            appendLine("14. JsonStreamingParser - Streaming JSON with backpressure")
            appendLine("15. JsonStreamingCCEK - Streaming with CCEK integration")
            appendLine("16. SimpleJsonSerializer - kotlinx.serialization integration")
            appendLine("17. TrikeShedJsonSerializer - TrikeShed-specific serialization")
            appendLine("18. BitmapJsonScanner - Original bitmap scanner (disabled)")
            appendLine("19. SimdBitmapOps - SIMD bitmap operations (disabled)")
            appendLine("20. SimdJsonScanner - SIMD-optimized scanner")
            appendLine("21. kotlinx.serialization - Standard library baseline")
            appendLine()
            appendLine("## Benchmark Categories")
            appendLine("- **Parsing Performance**: Raw parsing speed across different JSON sizes")
            appendLine("- **Query Performance**: Field access and lookup performance")
            appendLine("- **Memory Usage**: Heap allocation and garbage collection impact")
            appendLine("- **Large JSON Handling**: Scalability with very large documents")
            appendLine("- **Serialization Performance**: Encoding/decoding integration")
            appendLine("- **Streaming Performance**: Backpressure and flow handling")
            appendLine("- **Platform-Specific Performance**: Hardware acceleration benefits")
            appendLine("- **BBCursive Performance**: Functional parser performance")
            appendLine("- **Fingerprinting**: Structural hash generation")
            appendLine("- **Structural Analysis**: Document structure analysis")
            appendLine("- **Concurrent Access**: Multi-threaded access patterns")
            appendLine("- **Error Handling**: Malformed JSON processing")
            appendLine()
            appendLine("## Test Data Patterns")
            appendLine("- Simple objects: `{"field1":"value1",...}`")
            appendLine("- Arrays: `[{"id":1,"name":"item1",...},...]`")
            appendLine("- Nested structures: Deep object hierarchies")
            appendLine("- Large datasets: 100KB+ JSON documents")
            appendLine("- Mixed content: Objects, arrays, primitives")
            appendLine()
            appendLine("## Performance Metrics")
            appendLine("- **Throughput**: Operations per second")
            appendLine("- **Latency**: Nanoseconds per operation")
            appendLine("- **Memory**: Heap usage in KB")
            appendLine("- **GC Impact**: Garbage collection frequency")
            appendLine("- **Scalability**: Performance vs document size")
            appendLine("- **Concurrency**: Multi-threaded speedup")
            appendLine()
            appendLine("## Competition Results")
            appendLine("*(Results will be populated during benchmark execution)*")
            appendLine()
            appendLine("## Recommendations")
            appendLine("1. Use SimpleJsonScanner for small, simple JSON documents")
            appendLine("2. Use JsonScannerCompact for complex, nested structures")
            appendLine("3. Use FastJsonScanner when frequent field access is needed")
            appendLine("4. Use VectorizedJsonScanner on JVM for large documents")
            appendLine("5. Use BBCursive implementations for functional programming")
            appendLine("6. Use Streaming implementations for very large documents")
            appendLine("7. Use Platform-specific implementations when available")
        }
        
        // Write report to file
        val reportFile = java.io.File("json-benchmark-report-${System.currentTimeMillis()}.md")
        reportFile.writeText(report)
        
        println("📄 Detailed report written to: ${reportFile.absolutePath}")
    }
    
    private fun parseArgs(args: Array<String>): BenchmarkConfig {
        var runAll = false
        var runQuick = false
        var generateReport = false
        val runSpecific = mutableListOf<String>()
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--all", "-a" -> runAll = true
                "--quick", "-q" -> runQuick = true
                "--report", "-r" -> generateReport = true
                "--benchmark", "-b" -> {
                    if (i + 1 < args.size) {
                        runSpecific.add(args[i + 1])
                        i++
                    }
                }
                "--help", "-h" -> {
                    printUsage()
                    System.exit(0)
                }
            }
            i++
        }
        
        return BenchmarkConfig(runAll, runQuick, generateReport, runSpecific)
    }
    
    private fun printUsage() {
        println("""
            JSON Benchmark Runner Usage:
            
            --all, -a              Run all microbenchmarks
            --quick, -q            Run quick benchmark subset
            --benchmark, -b <name> Run specific benchmark
            --report, -r           Generate detailed report
            --help, -h             Show this help
            
            Examples:
            kotlin JsonBenchmarkRunner --all --report
            kotlin JsonBenchmarkRunner --quick
            kotlin JsonBenchmarkRunner -b ComprehensiveJsonBenchmark -b KotlinBenchmark
        """.trimIndent())
    }
    
    data class BenchmarkConfig(
        val runAll: Boolean,
        val runQuick: Boolean,
        val generateReport: Boolean,
        val runSpecific: List<String>
    )
} 