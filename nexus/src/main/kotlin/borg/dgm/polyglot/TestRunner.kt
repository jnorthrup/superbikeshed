package borg.dgm.polyglot

import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class TestRunner(
    private val dockerUtils: DockerUtils,
    private val logger: (String) -> Unit = { println(it) }
) {
    private val buildDir = File(System.getProperty("java.io.tmpdir"), "nexus-test-build")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        buildDir.mkdirs()
    }

    suspend fun runTests(testSpec: TestSpec): TestResult {
        return withContext(scope.coroutineContext) {
            try {
                // Build images in sequence
                dockerUtils.buildBaseImage(testSpec, buildDir)
                dockerUtils.buildEnvImage(testSpec, buildDir)
                dockerUtils.buildInstanceImage(testSpec, buildDir)

                // Run tests
                val runId = UUID.randomUUID().toString()
                dockerUtils.runTests(testSpec, runId)
            } catch (e: Exception) {
                logger("Error running tests: ${e.message}")
                TestResult(
                    success = false,
                    output = e.stackTraceToString(),
                    exitCode = -1
                )
            }
        }
    }

    suspend fun runTestsParallel(testSpecs: List<TestSpec>): Map<String, TestResult> {
        return withContext(scope.coroutineContext) {
            testSpecs.map { testSpec ->
                async {
                    testSpec.instanceId to runTests(testSpec)
                }
            }.awaitAll().toMap()
        }
    }

    fun cleanup() {
        scope.cancel()
        dockerUtils.cleanup()
        buildDir.deleteRecursively()
    }
} 