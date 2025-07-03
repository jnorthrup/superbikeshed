package borg.trikeshed.strace

/**
 * Simple test for the strace attention system
 */
object StraceTest {
    
    fun testEnums() {
        println("=== Testing Strace Enums ===")
        
        // Test syscall categorization
        val testSyscalls = listOf("read", "write", "socket", "fork", "chmod", "unknown")
        testSyscalls.forEach { syscall ->
            val category = SyscallCategory.fromSyscall(syscall)
            println("$syscall -> ${category.name} (weight: ${category.attentionWeight})")
        }
        
        println()
        
        // Test attention levels
        val testWeights = listOf(0.95, 0.75, 0.55, 0.35, 0.15, 0.05)
        testWeights.forEach { weight ->
            val level = AttentionLevel.fromWeight(weight)
            println("Weight $weight -> ${level.name}: ${level.description}")
        }
        
        println()
        
        // Test data patterns
        val frequencies = mapOf(
            "read" to 25,
            "write" to 5,
            "socket" to 8,
            "mmap" to 4
        )
        val pattern = DataPattern.detectPattern(listOf("read", "socket", "recv"), frequencies)
        println("Detected pattern: ${pattern.pattern} (confidence: ${pattern.confidence})")
        
        println()
        
        // Test error handling
        val testErrnos = listOf(1, 2, 4, 110, 111, 16, 22, 12, 122, 999)
        testErrnos.forEach { errno ->
            val error = StraceError.fromErrno(errno)
            println("Errno $errno -> ${error.code}: ${error.description} (${error.severity.description})")
        }
    }
    
    fun testStraceUtils() {
        println("=== Testing Strace Utils ===")
        
        // Test attention weight calculation
        val testCases = listOf(
            Triple("read", 15, 1024L),
            Triple("socket", 8, 0L),
            Triple("chmod", 1, 0L),
            Triple("unknown", 5, 512L)
        )
        
        testCases.forEach { (syscall, frequency, dataSize) ->
            val weight = StraceUtils.calculateAttentionWeight(syscall, frequency, dataSize, 0.1)
            println("$syscall (freq: $frequency, size: $dataSize) -> weight: $weight")
        }
        
        println()
        
        // Test LLM prompt generation
        val events = listOf(
            SyscallEvent(1000L, 1234, "read", listOf("3", "0x7fff", "1024"), 1024L, 5L),
            SyscallEvent(1005L, 1234, "write", listOf("1", "0x7fff", "50"), 50L, 2L),
            SyscallEvent(1007L, 1234, "socket", listOf("2", "1", "0"), 4L, 1L)
        )
        
        val weights = listOf(
            AttentionWeight("read", 0.8, "High data acquisition activity"),
            AttentionWeight("socket", 0.7, "Network communication detected")
        )
        
        val prompt = StraceUtils.generatePrompt(events, weights)
        println("Generated LLM Prompt:")
        println(prompt)
    }
    
    fun testStraceConfig() {
        println("=== Testing Strace Config ===")
        
        val config = StraceConfig(
            followForks = true,
            showTimestamps = true,
            showSyscallNumbers = false,
            maxEvents = 5000,
            filterSyscalls = setOf("read", "write", "socket"),
            attentionThreshold = 0.3
        )
        
        println("Config: $config")
        println("Strace flags: ${StraceFormat.buildStraceCommand(config)}")
        
        val modes = AnalysisMode.getDefaultModes()
        println("Default analysis modes: ${modes.map { it.name }}")
    }
} 