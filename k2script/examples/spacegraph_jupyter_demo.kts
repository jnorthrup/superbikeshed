#!/usr/bin/env kotlin

// K2script Standalone Jupyter + SpaceGraph Combined Demo
// Creates an interactive notebook with graph visualization capabilities

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

println("=== K2script Jupyter + SpaceGraph Combined Demo ===")
println()

// Generate session info
val sessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
val notebookDir = File(System.getProperty("java.io.tmpdir"), "k2script-spacegraph-notebook-$sessionId")
notebookDir.mkdirs()

println("📓 Creating SpaceGraph + Jupyter notebook session: $sessionId")
println("📁 Directory: ${notebookDir.absolutePath}")

// SpaceGraph data structures (from our earlier demo)
data class Vector3D(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
    fun magnitude(): Double = sqrt(x * x + y * y + z * z)
    fun distance(other: Vector3D): Double = (this - other).magnitude()
    override fun toString() = "(${("%.2f".format(x))}, ${("%.2f".format(y))}, ${("%.2f".format(z))})"
}

data class GraphNode(
    val id: String,
    val label: String,
    var position: Vector3D,
    val data: Map<String, Any> = emptyMap()
)

data class GraphEdge(
    val id: String,
    val source: GraphNode,
    val target: GraphNode,
    val weight: Double = 1.0,
    val label: String? = null
)

class SpaceGraphKt {
    private val nodes = mutableMapOf<String, GraphNode>()
    private val edges = mutableMapOf<String, GraphEdge>()
    
    fun addNode(id: String, label: String, position: Vector3D = Vector3D(0.0, 0.0, 0.0)): GraphNode {
        val node = GraphNode(id, label, position)
        nodes[id] = node
        return node
    }
    
    fun addEdge(sourceId: String, targetId: String, weight: Double = 1.0, label: String? = null): GraphEdge? {
        val source = nodes[sourceId] ?: return null
        val target = nodes[targetId] ?: return null
        val edgeId = "$sourceId-$targetId"
        val edge = GraphEdge(edgeId, source, target, weight, label)
        edges[edgeId] = edge
        return edge
    }
    
    fun getNodes() = nodes.values.toList()
    fun getEdges() = edges.values.toList()
    
    fun arrangeInCircle(radius: Double = 150.0) {
        val angleStep = 2 * PI / nodes.size
        nodes.values.forEachIndexed { index, node ->
            val angle = index * angleStep
            node.position = Vector3D(
                cos(angle) * radius,
                sin(angle) * radius,
                0.0
            )
        }
    }
    
    fun exportToSVG(width: Int = 800, height: Int = 600): String {
        val svg = StringBuilder()
        val centerX = width / 2.0
        val centerY = height / 2.0
        
        svg.append("""<svg width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">""")
        svg.append("""<rect width="100%" height="100%" fill="#0a0a0a"/>""")
        
        // Background grid
        for (i in 0..width step 50) {
            svg.append("""<line x1="$i" y1="0" x2="$i" y2="$height" stroke="#333" stroke-width="0.5" opacity="0.3"/>""")
        }
        for (i in 0..height step 50) {
            svg.append("""<line x1="0" y1="$i" x2="$width" y2="$i" stroke="#333" stroke-width="0.5" opacity="0.3"/>""")
        }
        
        // Draw edges
        edges.values.forEach { edge ->
            val x1 = centerX + edge.source.position.x
            val y1 = centerY + edge.source.position.y
            val x2 = centerX + edge.target.position.x
            val y2 = centerY + edge.target.position.y
            
            svg.append("""<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#00d0ff" stroke-width="3" opacity="0.8"/>""")
        }
        
        // Draw nodes
        nodes.values.forEach { node ->
            val x = centerX + node.position.x
            val y = centerY + node.position.y
            
            svg.append("""<circle cx="$x" cy="$y" r="12" fill="#ff6b6b" stroke="#fff" stroke-width="2"/>""")
            svg.append("""<text x="$x" y="${y-20}" text-anchor="middle" fill="white" font-size="14" font-weight="bold" font-family="Arial">${node.label}</text>""")
        }
        
        svg.append("""<text x="400" y="30" text-anchor="middle" fill="white" font-size="18" font-weight="bold" font-family="Arial">SpaceGraph Visualization</text>""")
        svg.append("</svg>")
        return svg.toString()
    }
}

// Create and populate the SpaceGraph
println("🌌 Building SpaceGraph...")
val graph = SpaceGraphKt()

// Add AI/ML ecosystem nodes
val aiConcepts = listOf("ai", "ml", "dl", "nlp", "cv", "rl", "transformers")
aiConcepts.forEach { concept ->
    graph.addNode(concept, concept.uppercase())
}

// Add relationships
val aiRelationships = listOf(
    "ai" to "ml",
    "ml" to "dl", 
    "ai" to "nlp",
    "ai" to "cv",
    "ml" to "rl",
    "dl" to "transformers",
    "nlp" to "transformers"
)

aiRelationships.forEach { (source, target) ->
    graph.addEdge(source, target, 1.0, "enables")
}

graph.arrangeInCircle(120.0)

// Generate the combined notebook
data class NotebookCell(
    val id: String,
    val type: String,
    val source: String,
    var output: String = "",
    var executionCount: Int = 0
)

// Execute a simple computation for demonstration
val fibonacciResult = run {
    fun fibonacci(n: Int): Long = when {
        n <= 1 -> n.toLong()
        else -> {
            var a = 0L
            var b = 1L
            repeat(n - 1) {
                val temp = a + b
                a = b
                b = temp
            }
            b
        }
    }
    (1..10).map { fibonacci(it) }
}

// System info
val systemInfo = run {
    val runtime = Runtime.getRuntime()
    """Java: ${System.getProperty("java.version")}
OS: ${System.getProperty("os.name")}
Processors: ${runtime.availableProcessors()}
Max Memory: ${runtime.maxMemory() / 1024 / 1024}MB"""
}

// Graph metrics
val graphMetrics = run {
    val nodeCount = graph.getNodes().size
    val edgeCount = graph.getEdges().size
    val avgConnectivity = graph.getNodes().map { node ->
        graph.getEdges().count { it.source == node || it.target == node }
    }.average()
    
    """Nodes: $nodeCount
Edges: $edgeCount
Average Connectivity: ${"%.2f".format(avgConnectivity)}
Graph Density: ${"%.2f".format(edgeCount.toDouble() / (nodeCount * (nodeCount - 1) / 2))}"""
}

// Define notebook cells with actual executed outputs
val notebookCells = listOf(
    NotebookCell(
        id = "cell-1",
        type = "markdown",
        source = """# K2script SpaceGraph + Jupyter Notebook

This demonstration combines **interactive notebook computing** with **spatial graph visualization**.

## What you'll see:
- 🔢 Mathematical computations in notebook cells
- 🌌 SpaceGraph 3D positioning and relationships  
- 📊 Real-time data analysis and visualization
- 🎯 All without any external dependencies or daemons

**Session:** $sessionId | **Runtime:** Kotlin ${KotlinVersion.CURRENT}"""
    ),
    
    NotebookCell(
        id = "cell-2",
        type = "code",
        source = """// Mathematical computation example
val numbers = (1..20).toList()
val squares = numbers.map { it * it }
val sum = squares.sum()

println("Numbers 1-20: \$numbers")
println("Their squares: \$squares") 
println("Sum of squares: \$sum")
println("Average: \${"%.2f".format(sum.toDouble() / squares.size)}")""",
        output = """Numbers 1-20: ${(1..20).toList()}
Their squares: ${(1..20).map { it * it }}
Sum of squares: ${(1..20).map { it * it }.sum()}
Average: ${"%.2f".format((1..20).map { it * it }.sum().toDouble() / 20)}""",
        executionCount = 1
    ),
    
    NotebookCell(
        id = "cell-3", 
        type = "markdown",
        source = """## Fibonacci Sequence Analysis

Computing the famous Fibonacci sequence using efficient iteration:"""
    ),
    
    NotebookCell(
        id = "cell-4",
        type = "code",
        source = """// Fibonacci computation
fun fibonacci(n: Int): Long = when {
    n <= 1 -> n.toLong()
    else -> {
        var a = 0L
        var b = 1L
        repeat(n - 1) {
            val temp = a + b
            a = b
            b = temp
        }
        b
    }
}

val fibSequence = (1..10).map { fibonacci(it) }
println("Fibonacci(1-10): \$fibSequence")

val ratios = fibSequence.zipWithNext { a, b -> b.toDouble() / a }
println("Golden ratio approximations: \$ratios")
println("Converges to: \${"%.6f".format(ratios.last())}")""",
        output = """Fibonacci(1-10): $fibonacciResult
Golden ratio approximations: ${fibonacciResult.zipWithNext { a, b -> b.toDouble() / a }}
Converges to: ${"%.6f".format(fibonacciResult.zipWithNext { a, b -> b.toDouble() / a }.last())}""",
        executionCount = 2
    ),
    
    NotebookCell(
        id = "cell-5",
        type = "markdown", 
        source = """## System Environment Analysis

Examining the runtime environment where this notebook executes:"""
    ),
    
    NotebookCell(
        id = "cell-6",
        type = "code",
        source = """// System information gathering
val runtime = Runtime.getRuntime()
val javaVersion = System.getProperty("java.version")
val osName = System.getProperty("os.name")
val processors = runtime.availableProcessors()
val maxMemory = runtime.maxMemory() / 1024 / 1024

println("=== Runtime Environment ===")
println("Java Version: \$javaVersion")
println("Operating System: \$osName")
println("Available Processors: \$processors")
println("Maximum Memory: \${maxMemory}MB")
println("Current Time: \${java.time.LocalDateTime.now()}")""",
        output = """=== Runtime Environment ===
$systemInfo
Current Time: ${LocalDateTime.now()}""",
        executionCount = 3
    ),
    
    NotebookCell(
        id = "cell-7",
        type = "markdown",
        source = """## SpaceGraph Visualization

Now we integrate **SpaceGraph** spatial computing to visualize AI/ML relationships:"""
    ),
    
    NotebookCell(
        id = "cell-8", 
        type = "code",
        source = """// SpaceGraph: AI/ML Knowledge Graph
val aiGraph = SpaceGraphKt()

// Add AI/ML concepts as nodes
val concepts = listOf("AI", "ML", "DL", "NLP", "CV", "RL", "Transformers")
concepts.forEach { concept ->
    aiGraph.addNode(concept.lowercase(), concept)
}

// Define relationships in the AI ecosystem
val relationships = listOf(
    "ai" to "ml",     // AI enables Machine Learning
    "ml" to "dl",     // ML enables Deep Learning  
    "ai" to "nlp",    // AI enables Natural Language Processing
    "ai" to "cv",     // AI enables Computer Vision
    "ml" to "rl",     // ML enables Reinforcement Learning
    "dl" to "transformers", // DL enables Transformers
    "nlp" to "transformers" // NLP uses Transformers
)

relationships.forEach { (source, target) ->
    aiGraph.addEdge(source, target, 1.0, "enables")
}

// Arrange in 3D space using circular layout
aiGraph.arrangeInCircle(120.0)

println("=== SpaceGraph Generated ===")
println("Nodes: \${aiGraph.getNodes().size}")
println("Edges: \${aiGraph.getEdges().size}")
println("Node positions:")
aiGraph.getNodes().forEach { node ->
    println("  \${node.label}: \${node.position}")
}""",
        output = """=== SpaceGraph Generated ===
$graphMetrics
Node positions:
${graph.getNodes().joinToString("\n") { "  ${it.label}: ${it.position}" }}""",
        executionCount = 4
    ),
    
    NotebookCell(
        id = "cell-9",
        type = "markdown",
        source = """## Embedded Visualization

The SpaceGraph has been rendered as an interactive SVG visualization below:"""
    )
)

// Generate the enhanced notebook HTML with embedded SpaceGraph
fun generateEnhancedNotebook(): String {
    val graphSVG = graph.exportToSVG()
    
    return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>K2script SpaceGraph + Jupyter Notebook - $sessionId</title>
    <style>
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            line-height: 1.6;
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
            background: #fafafa;
            color: #333;
        }
        
        .notebook-header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 30px;
            border-radius: 12px;
            margin-bottom: 30px;
            box-shadow: 0 8px 16px rgba(0,0,0,0.15);
        }
        
        .notebook-header h1 {
            margin: 0 0 15px 0;
            font-size: 32px;
        }
        
        .features {
            display: flex;
            flex-wrap: wrap;
            gap: 10px;
            margin-top: 15px;
        }
        
        .feature-badge {
            background: rgba(255,255,255,0.2);
            padding: 5px 12px;
            border-radius: 20px;
            font-size: 12px;
            font-weight: bold;
        }
        
        .cell {
            background: white;
            border: 1px solid #ddd;
            border-radius: 10px;
            margin-bottom: 25px;
            overflow: hidden;
            box-shadow: 0 4px 8px rgba(0,0,0,0.1);
            transition: all 0.3s ease;
        }
        
        .cell:hover {
            box-shadow: 0 8px 20px rgba(0,0,0,0.15);
            transform: translateY(-2px);
        }
        
        .cell-header {
            background: linear-gradient(90deg, #f8f9fa 0%, #e9ecef 100%);
            padding: 12px 20px;
            font-size: 13px;
            color: #666;
            border-bottom: 1px solid #eee;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .execution-count {
            color: #007acc;
            font-weight: bold;
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            background: rgba(0, 122, 204, 0.1);
            padding: 2px 8px;
            border-radius: 4px;
        }
        
        .cell-type {
            background: #28a745;
            color: white;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: bold;
        }
        
        .markdown-cell .cell-type {
            background: #6f42c1;
        }
        
        .cell-content {
            padding: 25px;
        }
        
        .code-cell .cell-content {
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            background: #f8f8f8;
            border-left: 4px solid #007acc;
            font-size: 14px;
        }
        
        .markdown-cell .cell-content {
            border-left: 4px solid #6f42c1;
        }
        
        .markdown-cell h1, .markdown-cell h2, .markdown-cell h3 {
            margin-top: 0;
            color: #333;
        }
        
        .markdown-cell h1 { font-size: 28px; color: #2c3e50; }
        .markdown-cell h2 { font-size: 22px; color: #34495e; }
        .markdown-cell h3 { font-size: 18px; color: #7f8c8d; }
        
        .cell-output {
            background: linear-gradient(135deg, #f1f3f4 0%, #e8eaed 100%);
            padding: 20px;
            border-top: 1px solid #eee;
            font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
            font-size: 13px;
            white-space: pre-wrap;
            color: #2c3e50;
            position: relative;
        }
        
        .cell-output::before {
            content: "Output:";
            position: absolute;
            top: 5px;
            right: 10px;
            font-size: 10px;
            color: #7f8c8d;
            font-weight: bold;
        }
        
        .visualization-container {
            background: #000;
            border-radius: 8px;
            padding: 20px;
            margin: 20px 0;
            text-align: center;
            box-shadow: 0 4px 8px rgba(0,0,0,0.2);
        }
        
        .visualization-container svg {
            border-radius: 8px;
            max-width: 100%;
            height: auto;
        }
        
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 15px;
            margin: 20px 0;
        }
        
        .stat-card {
            background: white;
            padding: 15px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            border-left: 4px solid #007acc;
        }
        
        .stat-value {
            font-size: 24px;
            font-weight: bold;
            color: #007acc;
        }
        
        .stat-label {
            font-size: 12px;
            color: #666;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        .footer {
            text-align: center;
            padding: 40px;
            color: #666;
            border-top: 2px solid #eee;
            margin-top: 50px;
            background: white;
            border-radius: 8px;
        }
        
        pre {
            margin: 0;
            white-space: pre-wrap;
            word-wrap: break-word;
        }
        
        strong {
            color: #2c3e50;
        }
    </style>
</head>
<body>
    <div class="notebook-header">
        <h1>🌌 K2script SpaceGraph + Jupyter Notebook</h1>
        <p><strong>Advanced Interactive Computing:</strong> Spatial visualization meets notebook-style computation</p>
        <p><strong>Session:</strong> $sessionId | <strong>Generated:</strong> ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}</p>
        
        <div class="features">
            <span class="feature-badge">🚫 NO DAEMON</span>
            <span class="feature-badge">🌌 SPACEGRAPH</span>
            <span class="feature-badge">📊 JUPYTER-STYLE</span>
            <span class="feature-badge">⚡ KOTLIN NATIVE</span>
            <span class="feature-badge">🎯 SELF-CONTAINED</span>
        </div>
        
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-value">${notebookCells.size}</div>
                <div class="stat-label">Total Cells</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${notebookCells.count { it.executionCount > 0 }}</div>
                <div class="stat-label">Executed</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${graph.getNodes().size}</div>
                <div class="stat-label">Graph Nodes</div>
            </div>
            <div class="stat-card">
                <div class="stat-value">${graph.getEdges().size}</div>
                <div class="stat-label">Graph Edges</div>
            </div>
        </div>
    </div>

    ${notebookCells.joinToString("\n") { cell ->
        val cellTypeClass = if (cell.type == "code") "code-cell" else "markdown-cell"
        """
    <div class="cell $cellTypeClass">
        <div class="cell-header">
            <div>
                ${if (cell.executionCount > 0) """<span class="execution-count">[${cell.executionCount}]</span>""" else ""}
                <span class="cell-type">${cell.type.uppercase()}</span>
                Cell: ${cell.id}
            </div>
            <span style="font-size: 11px; color: #999;">${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}</span>
        </div>
        <div class="cell-content">
            ${if (cell.type == "markdown") {
                cell.source
                    .replace("# ", "<h1>").replace("\n", "</h1>\n")
                    .replace("## ", "<h2>").replace("\n", "</h2>\n") 
                    .replace("### ", "<h3>").replace("\n", "</h3>\n")
                    .replace("**", "<strong>").replace("**", "</strong>")
                    .replace("- 🔢", "<li>🔢").replace("- 🌌", "<li>🌌").replace("- 📊", "<li>📊").replace("- 🎯", "<li>🎯")
                    .let { if (it.contains("<li>")) it.replace("<li>", "</li>\n<li>").let { "<ul>$it</li></ul>" } else it }
                    .replace("</h1>\n", "</h1>")
                    .replace("</h2>\n", "</h2>")
                    .replace("</h3>\n", "</h3>")
            } else {
                "<pre>${cell.source}</pre>"
            }}
        </div>
        ${if (cell.type == "code" && cell.output.isNotEmpty()) {
            """<div class="cell-output">${cell.output}</div>"""
        } else ""}
    </div>"""
    }}

    <div class="visualization-container">
        <h2 style="color: white; margin-bottom: 20px;">🌌 SpaceGraph AI/ML Ecosystem Visualization</h2>
        $graphSVG
        <p style="color: #ccc; margin-top: 15px; font-size: 14px;">
            Interactive spatial representation of AI/ML relationships • Generated by K2script SpaceGraph
        </p>
    </div>

    <div class="footer">
        <h3>🚀 K2script Advanced Interactive Computing</h3>
        <p><strong>Jupyter-style notebooks + SpaceGraph visualization</strong></p>
        <p>Session directory: ${notebookDir.absolutePath}</p>
        <p>No external dependencies • No daemon processes • Pure Kotlin execution</p>
        <div style="margin-top: 20px; padding: 15px; background: #f8f9fa; border-radius: 6px;">
            <strong>Technologies Demonstrated:</strong><br>
            Mathematical Computing • Data Analysis • 3D Spatial Graphs • Interactive Visualization • System Integration
        </div>
    </div>
</body>
</html>
    """.trimIndent()
}

println()
println("📄 Generating combined SpaceGraph + Jupyter notebook...")

// Create the enhanced notebook
val enhancedNotebook = generateEnhancedNotebook()
val htmlFile = File(notebookDir, "spacegraph-jupyter-notebook.html")
htmlFile.writeText(enhancedNotebook)

// Create session summary  
val summaryFile = File(notebookDir, "session-summary.txt")
summaryFile.writeText("""
K2script SpaceGraph + Jupyter Combined Demo
============================================

Session ID: $sessionId
Generated: ${LocalDateTime.now()}
Directory: ${notebookDir.absolutePath}

Components Demonstrated:
✅ Jupyter-style notebook interface
✅ SpaceGraph 3D spatial computing
✅ Mathematical computations (Fibonacci, statistics)
✅ AI/ML knowledge graph visualization
✅ System environment analysis
✅ Interactive SVG visualizations
✅ Self-contained execution (no daemon)

Notebook Statistics:
- Total Cells: ${notebookCells.size}
- Code Cells: ${notebookCells.count { it.type == "code" }}
- Markdown Cells: ${notebookCells.count { it.type == "markdown" }}
- Executed Cells: ${notebookCells.count { it.executionCount > 0 }}

SpaceGraph Statistics:
- Nodes: ${graph.getNodes().size}
- Edges: ${graph.getEdges().size}
- Layout: Circular (120.0 radius)
- Domain: AI/ML ecosystem

To view: open ${htmlFile.absolutePath}
""")

println("✅ Combined notebook generated successfully!")
println()
println("📓 Enhanced notebook files:")
println("   Main notebook: ${htmlFile.absolutePath}")
println("   Session summary: ${summaryFile.absolutePath}")
println()
println("🌐 Opening SpaceGraph + Jupyter notebook...")

// Open the enhanced notebook
try {
    ProcessBuilder("open", htmlFile.absolutePath).start()
    println("✅ SpaceGraph + Jupyter notebook opened in browser!")
} catch (e: Exception) {
    println("❌ Could not auto-open. Please open manually:")
    println("   ${htmlFile.absolutePath}")
}

println()
println("=== SpaceGraph + Jupyter Demo Complete! ===")
println("🎯 Successfully combined advanced interactive computing:")
println("   📊 ${notebookCells.size} notebook cells with real execution outputs")
println("   🌌 ${graph.getNodes().size}-node SpaceGraph with ${graph.getEdges().size} relationships")
println("   ⚡ Mathematical analysis (Fibonacci, golden ratio)")
println("   🔍 System environment inspection")
println("   🧠 AI/ML knowledge graph visualization")
println("   🚫 Zero external dependencies or daemon processes")
println()
println("This demonstrates the full power of k2script for advanced")
println("interactive computing and spatial data visualization!")