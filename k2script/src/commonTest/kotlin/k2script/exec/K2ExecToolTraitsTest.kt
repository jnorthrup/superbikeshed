@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
package k2script.exec

import kotlin.test.*
import kotlinx.coroutines.runBlocking

class K2ExecToolTraitsTest {
    @Test
    fun testDependencyResolutionTrait() = runBlocking {
        val tool = K2ExecTool()
        val result = tool.quickResolve(1 j { "org.jetbrains.kotlin:kotlin-stdlib:1.9.24" })
        assertTrue(result.resolved.a >= 1, "Should resolve at least one dependency")
    }

    @Test
    fun testCacheManagementTrait() = runBlocking {
        val tool = K2ExecTool()
        val statsBefore = tool.getCacheStats()
        tool.preWarmCache(1 j { "org.jetbrains.kotlin:kotlin-stdlib:1.9.24" })
        val statsAfter = tool.getCacheStats()
        assertTrue(statsAfter.totalEntries >= statsBefore.totalEntries, "Cache entries should increase or remain the same after pre-warming")
    }

    @Test
    fun testReactorBasedParallelismTrait() = runBlocking {
        val tool = K2ExecTool()
        val dependencies = 3 j { i -> "org.jetbrains.kotlin:kotlin-stdlib:${1.9 + i * 0.01}" }
        tool.preWarmCache(dependencies) // Should run in parallel
        val stats = tool.getCacheStats()
        assertTrue(stats.totalEntries >= 3, "Should cache multiple dependencies in parallel")
    }

    @Test
    fun testBandwidthProfileTrait() = runBlocking {
        val config = ExecConfig(bandwidthProfile = BandwidthProfile.LOW)
        val tool = K2ExecTool()
        val result = tool.execute("dummy.kts", config)
        // No real script, but config should be accepted and not throw
        assertNotNull(result)
    }

    @Test
    fun testDryRunTrait() = runBlocking {
        val config = ExecConfig(dryRun = true)
        val tool = K2ExecTool()
        val result = tool.execute("dummy.kts", config)
        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("Dry run"), "Output should indicate dry run")
    }

    @Test
    fun testVerboseOutputTrait() = runBlocking {
        val config = ExecConfig(verboseOutput = true, dryRun = true)
        val tool = K2ExecTool()
        val result = tool.execute("dummy.kts", config)
        assertEquals(0, result.exitCode)
        // No assertion on output, but should not throw
    }

    @Test
    fun testPreWarmCacheTrait() = runBlocking {
        val tool = K2ExecTool()
        val deps = 2 j { i -> "org.jetbrains.kotlin:kotlin-stdlib:${1.9 + i * 0.01}" }
        tool.preWarmCache(deps)
        val stats = tool.getCacheStats()
        assertTrue(stats.totalEntries >= 2, "Pre-warming should cache dependencies")
    }

    @Test
    fun testAgentResolutionTrait() = runBlocking {
        val tool = K2ExecTool()
        val nexus = tool.getToolsForNexus()
        val result = nexus.resolveForAgent("agent1", 1 j { "org.jetbrains.kotlin:kotlin-stdlib:1.9.24" })
        assertTrue(result.success, "Agent resolution should succeed")
        assertTrue(result.resolvedCount >= 1, "Should resolve at least one dependency for agent")
    }
} 