package nexus.examples

import nexus.ai.providers.*
import kotlinx.coroutines.*

/**
 * Simple runner for the integrated pipeline example
 */
object RunPipeline {
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        try {
            // Configure providers
            System.setProperty("NEXUS_DEFAULT_PROVIDER", "mock")
            
            println("Starting Nexus Integrated Pipeline Demo...")
            println("=" * 50)
            
            // Run the pipeline
            IntegratedPipeline.main(args)
            
        } catch (e: Exception) {
            println("Error running pipeline: ${e.message}")
            e.printStackTrace()
        }
    }
}

private operator fun String.times(n: Int) = this.repeat(n)