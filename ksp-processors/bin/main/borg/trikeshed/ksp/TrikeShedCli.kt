package borg.trikeshed.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * Unified command-line interface and code DSL.
 * Maps interest pathways from main() and provides DSL for code generation.
 */
class TrikeShedCli(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) {

    fun main(args: Array<String>) {
        when (args.firstOrNull()) {
            "trace" -> tracePathways(args.drop(1))
            "generate" -> generateCode(args.drop(1))
            "mermaid" -> generateMermaid(args.drop(1))
            else -> showUsage()
        }
    }

    private fun tracePathways(args: List<String>) {
        // Trace execution pathways from main() outward
        logger.warn("Tracing pathways...")
    }

    private fun generateCode(args: List<String>) {
        // Generate code using DSL
        logger.warn("Generating code...")
    }

    private fun generateMermaid(args: List<String>) {
        // Generate Mermaid diagrams
        logger.warn("Generating Mermaid...")
    }

    private fun showUsage() {
        logger.warn("""
        TrikeShed CLI/DSL:
        
        trace [options]    - Trace execution pathways from main()
        generate [type]    - Generate code using DSL
        mermaid [type]     - Generate Mermaid diagrams
        """.trimIndent())
    }

    // DSL builders
    fun pathway(block: PathwayBuilder.() -> Unit): String {
        val builder = PathwayBuilder()
        builder.block()
        return builder.build()
    }

    class PathwayBuilder {
        private val nodes = mutableListOf<String>()
        
        fun main() { nodes.add("main()") }
        fun call(name: String) { nodes.add("$name()") }
        fun branch(condition: String, block: PathwayBuilder.() -> Unit) {
            nodes.add("if($condition)")
            val subBuilder = PathwayBuilder()
            subBuilder.block()
            nodes.addAll(subBuilder.nodes.map { "  $it" })
        }
        
        fun build(): String {
            return """
            ```mermaid
            graph TD
            ${nodes.mapIndexed { i, node -> "    n$i[\"$node\"]" }.joinToString("\n")}
            ${(0 until nodes.size - 1).map { "    n$it --> n${it + 1}" }.joinToString("\n")}
            ```
            """.trimIndent()
        }
    }
}