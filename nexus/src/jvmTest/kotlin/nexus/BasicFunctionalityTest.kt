package nexus

import kotlinx.coroutines.runBlocking
import nexus.scanner.EnvironmentScanner
import nexus.tools.ToolOrchestrator
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic functionality tests for Nexus
 */
class BasicFunctionalityTest {
    
    @Test
    fun `environment scanner detects project structure`() = runBlocking {
        val scanner = EnvironmentScanner(File("."))
        val info = scanner.scan()
        
        // Should detect Kotlin and Gradle in this project
        assertTrue(info.languages.contains(EnvironmentScanner.Language.KOTLIN))
        assertTrue(info.buildTools.contains(EnvironmentScanner.BuildTool.GRADLE))
        assertTrue(info.projectType == EnvironmentScanner.ProjectType.KOTLIN_MULTIPLATFORM)
    }
    
    @Test
    fun `tool orchestrator can discover tools`() = runBlocking {
        val orchestrator = ToolOrchestrator(File("."))
        val tools = orchestrator.discoverTools()
        
        // Should find at least some tools
        assertTrue(tools.isNotEmpty())
        
        // Check if common tools are detected
        val toolNames = tools.keys.toSet()
        assertTrue(toolNames.contains("gradle") || toolNames.contains("gradle-wrapper"))
    }
    
    @Test
    fun `configuration parsing works`() {
        val args = arrayOf("--verbose", "--ai-provider", "openai", "scan", "src")
        val (config, remainingArgs) = NexusConfig.fromArgs(args)
        
        assertTrue(config.verbose)
        assertEquals("openai", config.aiProvider)
        assertEquals(listOf("scan", "src"), remainingArgs)
    }
    
    @Test
    fun `help command works`() {
        val helpText = Nexus.printHelp()
        assertTrue(helpText.contains("Usage: nexus"))
        assertTrue(helpText.contains("Commands:"))
        assertTrue(helpText.contains("Options:"))
    }
} 