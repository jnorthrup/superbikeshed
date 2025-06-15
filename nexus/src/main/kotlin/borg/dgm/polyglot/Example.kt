package borg.dgm.polyglot

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    // Create test specifications for different languages
    val pythonTest = TestSpec(
        instanceId = "python-example",
        repo = "https://github.com/example/python-test",
        repoScriptList = listOf(
            "git clone https://github.com/example/python-test .",
            "pip install -r requirements.txt"
        ),
        evalScriptList = listOf(
            "python -m pytest tests/"
        ),
        envScriptList = listOf(
            "apt-get update",
            "apt-get install -y python3 python3-pip",
            "pip3 install pytest"
        ),
        arch = "x86_64"
    )

    val rustTest = TestSpec(
        instanceId = "rust-example",
        repo = "https://github.com/example/rust-test",
        repoScriptList = listOf(
            "git clone https://github.com/example/rust-test .",
            "cargo build"
        ),
        evalScriptList = listOf(
            "cargo test"
        ),
        envScriptList = listOf(
            "apt-get update",
            "apt-get install -y curl",
            "curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y",
            "source $HOME/.cargo/env"
        ),
        arch = "x86_64"
    )

    // Create test runner
    val dockerUtils = DockerUtils()
    val testRunner = TestRunner(dockerUtils)

    try {
        // Run tests in parallel
        val results = testRunner.runTestsParallel(listOf(pythonTest, rustTest))

        // Print results
        results.forEach { (instanceId, result) ->
            println("Test results for $instanceId:")
            println("Success: ${result.success}")
            println("Exit code: ${result.exitCode}")
            println("Output:")
            println(result.output)
            println("---")
        }
    } finally {
        testRunner.cleanup()
    }
} 