#!/usr/bin/env kotlin

import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

// Run Percolator Demo
println("""
╔════════════════════════════════════════════╗
║        PERCOLATOR DEMO LAUNCHER            ║
╚════════════════════════════════════════════╝

This will start:
1. Coordinator server on port 8888
2. Sample volunteer node

Building percolator components...
""")

// Build the project
val buildProcess = ProcessBuilder("./gradlew", ":fiduciary:build", "-q")
    .inheritIO()
    .start()
buildProcess.waitFor()

if (buildProcess.exitValue() != 0) {
    println("❌ Build failed")
    exitProcess(1)
}

// Start coordinator server
println("🌊 Starting coordinator server...")
val coordinatorProcess = ProcessBuilder(
    "java", "-cp", "fiduciary/build/libs/fiduciary-jvm.jar",
    "fiduciary.percolator.PercolatorCoordinatorServerKt"
).inheritIO().start()

// Wait for coordinator to start
println("⏳ Waiting for coordinator to initialize...")
Thread.sleep(5000)

// Check if coordinator is running
if (!coordinatorProcess.isAlive) {
    println("❌ Failed to start coordinator server")
    exitProcess(1)
}

println("✅ Coordinator running at http://localhost:8888")
println()

// Start volunteer node
println("🤖 Starting volunteer node...")
val nodeProcess = ProcessBuilder(
    "java", "-cp", "fiduciary/build/libs/fiduciary-jvm.jar",
    "fiduciary.percolator.RunPercolatorNodeKt",
    "--coordinator=http://localhost:8888",
    "--max-concurrent=3"
).inheritIO().start()

// Wait for node to connect
Thread.sleep(3000)

// Check if node is running
if (!nodeProcess.isAlive) {
    println("❌ Failed to start volunteer node")
    coordinatorProcess.destroy()
    exitProcess(1)
}

println("""
✅ Volunteer node connected

════════════════════════════════════════════
🎯 PERCOLATOR NETWORK RUNNING
════════════════════════════════════════════

Dashboard: http://localhost:8888
API: http://localhost:8888/api/v1

The volunteer node will:
- Claim work units from coordinator
- Extract content via range requests
- Process through NLP pipeline
- Submit results back to network

Press Ctrl+C to stop the demo
""")

// Register shutdown hook
Runtime.getRuntime().addShutdownHook(thread {
    println("\n🛑 Shutting down percolator demo...")
    coordinatorProcess.destroy()
    nodeProcess.destroy()
    println("✅ Demo stopped cleanly")
})

// Keep script running
coordinatorProcess.waitFor()
nodeProcess.waitFor()