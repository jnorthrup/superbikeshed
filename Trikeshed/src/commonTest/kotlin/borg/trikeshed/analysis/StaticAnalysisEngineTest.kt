package borg.trikeshed.analysis

import kotlin.test.*
import borg.trikeshed.analysis.*

class StaticAnalysisEngineTest {
    @Test
    fun testSimpleClassAndFunctionDetection() {
        val code = """
            class Foo {}
            fun bar() {}
        """.trimIndent()

        val graph = StaticAnalysisEngine.analyze(code)
        val nodeNames = graph.nodes.play.map { it.entity.name }
        val nodeTypes = graph.nodes.play.map { it.entity.type }
        val confidences = graph.nodes.play.map { it.entity.confidence }

        // Should find both the class and the function
        assertTrue(nodeNames.contains("Foo"), "Should detect class Foo")
        assertTrue(nodeNames.contains("bar"), "Should detect function bar")
        assertTrue(nodeTypes.contains("ClassOrObject"), "Should have ClassOrObject node")
        assertTrue(nodeTypes.contains("Function"), "Should have Function node")
        // Confidence should be in the expected range
        assertTrue(confidences.all { it in 0.7f..0.9f }, "Confidences should be in expected range")
    }
} 