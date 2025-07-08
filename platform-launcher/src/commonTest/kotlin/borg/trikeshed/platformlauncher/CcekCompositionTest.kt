package borg.trikeshed.platformlauncher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CcekCompositionTest {

    @Test
    fun testTaskDispatcherWithFullContext() = runTest {
        val k2scriptCtx = K2ScriptContext("/tmp/k2sandbox")
        val nexusCtx = NexusContext("main-orchestrator-1")
        val couchDbCtx = CouchDBContext("http://localhost:5984/mydb")
        val bfdPlatformCtx = BFDPlatformContext("bfd-prod-cluster")

        val context = k2scriptCtx + nexusCtx + couchDbCtx + bfdPlatformCtx

        val expectedOutput = "Task dispatched with: " +
                "K2Script: /tmp/k2sandbox, " +
                "Nexus: main-orchestrator-1, " +
                "CouchDB: http://localhost:5984/mydb, " +
                "BFD Platform: bfd-prod-cluster"

        val actualOutput = TaskDispatcher(context)

        assertEquals(expectedOutput, actualOutput)
    }

    @Test
    fun testTaskDispatcherWithPartialContext() = runTest {
        val k2scriptCtx = K2ScriptContext("/tmp/k2sandbox")
        val nexusCtx = NexusContext("main-orchestrator-1")

        val context = k2scriptCtx + nexusCtx

        val expectedOutput = "Task dispatched with: " +
                "K2Script: /tmp/k2sandbox, " +
                "Nexus: main-orchestrator-1, " +
                "CouchDB: null, " +
                "BFD Platform: null"

        val actualOutput = TaskDispatcher(context)

        assertEquals(expectedOutput, actualOutput)
    }
}