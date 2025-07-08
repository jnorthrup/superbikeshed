package nexus.integration

import kotlinx.coroutines.runBlocking
import nexus.scanner.EnvironmentScanner
import nexus.Main.NexusConfig
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for new Nexus architecture
 */
class NexusIntegrationTest {
    
    @Test
    fun `environment scanner detects Kotlin project`() = runBlocking {
        val scanner = EnvironmentScanner(File("."))
        val info = scanner.scan()
        
        assertTrue(info.languages.contains(EnvironmentScanner.Language.KOTLIN))
        assertTrue(info.buildTools.contains(EnvironmentScanner.BuildTool.GRADLE))
    }
    
    @Test
    fun `command line parsing works correctly`() {
        val args = arrayOf("--verbose", "--ai-provider", "openai", "scan", "src")
        val (config, remainingArgs) = NexusConfig.fromArgs(args)
        
        assertTrue(config.verbose)
        assertEquals("openai", config.aiProvider)
        assertEquals(listOf("scan", "src"), remainingArgs)
    }
}