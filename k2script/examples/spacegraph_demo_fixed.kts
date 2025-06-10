#!/usr/bin/env k2script

// SpaceGraph Kotlin Demo - Fixed visualization with working layout
// Updated to use TrikeShed patterns: Series<T>, Join<A,B>, j operator, α transforms

@file:Import("k2script.trikeshed.*")

println("=== SpaceGraph Kotlin Demo (Fixed Layout) ===")
println()

// Define basic graph structures
data class Vector3D(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
    fun magnitude(): Double = kotlin.math.sqrt(x * x + y * y + z * z)
    fun normalized(): Vector3D = if (magnitude() > 0) this * (1.0 / magnitude()) else Vector3D(0.0, 0.0, 0.0)
    fun distance(other: Vector3D): Double = (this - other).magnitude()
    override fun toString() = "(${"%.2f".format(x)}, ${"%.2f".format(y)}, ${"%.2f".format(z)})"
}

@JvmInline
value class NodeData(val entries: Series<Join<String, Any>>)

data class GraphNode(
    val id: String,
    val label: String,
    var position: Vector3D,
    val data: NodeData = NodeData(Series.empty())
)

data class GraphEdge(
    val id: String,
    val source: GraphNode,
    val target: GraphNode,
    val weight: Double = 1.0,
    val label: String? = null
)

class SpaceGraphKt {
    private var nodes = Series.empty<Join<String, GraphNode>>()
    private var edges = Series.empty<Join<String, GraphEdge>>()
    
    fun addNode(id: String, label: String, position: Vector3D = Vector3D(0.0, 0.0, 0.0), data: NodeData = NodeData(Series.empty())): GraphNode {
        val node = GraphNode(id, label, position, data)
        val nodeEntry = id j node
        nodes = Series.of(*nodes.▶.toTypedArray(), nodeEntry)
        println("  Added node: $id -> $label at $position")
        return node
    }
    
    private fun findNode(id: String): GraphNode? {
        return nodes.▶.find { it.first == id }?.second
    }
    
    fun addEdge(sourceId: String, targetId: String, weight: Double = 1.0, label: String? = null): GraphEdge? {
        val source = findNode(sourceId) ?: return null.also { println("  Error: Source node $sourceId not found") }
        val target = findNode(targetId) ?: return null.also { println("  Error: Target node $targetId not found") }
        
        val edgeId = "$sourceId-$targetId"
        val edge = GraphEdge(edgeId, source, target, weight, label)
        val edgeEntry = edgeId j edge
        edges = Series.of(*edges.▶.toTypedArray(), edgeEntry)
        println("  Added edge: $sourceId -> $targetId (weight: $weight)")
        return edge
    }
    
    fun getNodes(): Series<GraphNode> = nodes.α { it.second }
    fun getEdges(): Series<GraphEdge> = edges.α { it.second }
    
    fun calculateGraphMetrics() {
        println("Graph Metrics:")
        println("  Nodes: ${nodes.size}")
        println("  Edges: ${edges.size}")
        
        val nodeList = getNodes()
        val edgeList = getEdges()
        
        val connectivity = nodeList.α { node ->
            edgeList.▶.count { it.source == node || it.target == node }
        }
        println("  Average connectivity: ${"%.2f".format(connectivity.▶.average())}")
        
        val maxPossibleEdges = nodes.size * (nodes.size - 1) / 2
        val density = if (maxPossibleEdges > 0) edges.size.toDouble() / maxPossibleEdges else 0.0
        println("  Graph density: ${"%.2f".format(density)}")
    }
    
    fun findShortestPath(startId: String, endId: String): Series<GraphNode>? {
        val start = findNode(startId) ?: return null
        val end = findNode(endId) ?: return null
        
        val queue = mutableListOf(Series.of(start))
        val visited = mutableSetOf<String>()
        
        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            val current = path.▶.last()
            
            if (current == end) return path
            if (current.id in visited) continue
            visited.add(current.id)
            
            val edgeList = getEdges()
            val connectedNodes = edgeList.▶.filter { 
                it.source == current || it.target == current 
            }.map { 
                if (it.source == current) it.target else it.source 
            }
            
            connectedNodes.forEach { neighbor ->
                if (neighbor.id !in visited) {
                    val newPath = Series.of(*path.▶.toTypedArray(), neighbor)
                    queue.add(newPath)
                }
            }
        }
        return null
    }
    
    fun arrangeInCircle() {
        println("Arranging nodes in a circular layout...")
        val radius = 150.0
        val angleStep = 2 * kotlin.math.PI / nodes.size
        
        nodes.values.forEachIndexed { index, node ->
            val angle = index * angleStep
            node.position = Vector3D(
                kotlin.math.cos(angle) * radius,
                kotlin.math.sin(angle) * radius,
                0.0
            )
        }
    }
    
    fun exportToSVG(): String {
        val width = 800
        val height = 600
        val svg = StringBuilder()
        
        svg.append("""<svg width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">""")
        svg.append("""<rect width="100%" height="100%" fill="#0a0a0a"/>""")
        
        // Add background grid
        for (i in 0..width step 50) {
            svg.append("""<line x1="$i" y1="0" x2="$i" y2="$height" stroke="#333" stroke-width="0.5" opacity="0.3"/>""")
        }
        for (i in 0..height step 50) {
            svg.append("""<line x1="0" y1="$i" x2="$width" y2="$i" stroke="#333" stroke-width="0.5" opacity="0.3"/>""")
        }
        
        // Center the graph
        val centerX = width / 2.0
        val centerY = height / 2.0
        
        // Draw edges with glow effect
        edges.values.forEach { edge ->
            val x1 = centerX + edge.source.position.x
            val y1 = centerY + edge.source.position.y
            val x2 = centerX + edge.target.position.x  
            val y2 = centerY + edge.target.position.y
            
            // Glow effect
            svg.append("""<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#00d0ff" stroke-width="6" opacity="0.3"/>""")
            // Main line
            svg.append("""<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#00d0ff" stroke-width="2"/>""")
            
            // Edge label
            val midX = (x1 + x2) / 2
            val midY = (y1 + y2) / 2
            edge.label?.let { label ->
                svg.append("""<text x="$midX" y="$midY" text-anchor="middle" fill="#888" font-size="10" font-family="Arial">$label</text>""")
            }
        }
        
        // Draw nodes with glow effect
        nodes.values.forEach { node ->
            val x = centerX + node.position.x
            val y = centerY + node.position.y
            
            // Glow effect
            svg.append("""<circle cx="$x" cy="$y" r="15" fill="#ff6b6b" opacity="0.4"/>""")
            // Main node
            svg.append("""<circle cx="$x" cy="$y" r="10" fill="#ff6b6b" stroke="#fff" stroke-width="2"/>""")
            // Node label
            svg.append("""<text x="$x" y="${y-20}" text-anchor="middle" fill="white" font-size="14" font-weight="bold" font-family="Arial">${node.label}</text>""")
        }
        
        // Title
        svg.append("""<text x="400" y="30" text-anchor="middle" fill="white" font-size="20" font-weight="bold" font-family="Arial">Kotlin Ecosystem Graph</text>""")
        
        svg.append("</svg>")
        return svg.toString()
    }
}

// Create the SpaceGraph
println("Creating Kotlin ecosystem SpaceGraph...")
val graph = SpaceGraphKt()

// Add nodes for the Kotlin ecosystem
val concepts = listOf(
    "kotlin",
    "jvm", 
    "android",
    "coroutines",
    "multiplatform",
    "javascript",
    "native"
)

concepts.forEach { label ->
    graph.addNode(label, label.replaceFirstChar { it.uppercase() }, Vector3D(0.0, 0.0, 0.0), mapOf("type" to "concept"))
}

// Add relationships
println()
val relationships = listOf(
    "kotlin" to "jvm",
    "kotlin" to "android", 
    "kotlin" to "coroutines",
    "kotlin" to "multiplatform",
    "multiplatform" to "javascript",
    "multiplatform" to "native",
    "jvm" to "android",
    "android" to "coroutines"
)

relationships.forEach { (source, target) ->
    graph.addEdge(source, target, 1.0, "enables")
}

println()
graph.calculateGraphMetrics()

// Test pathfinding
println()
println("Finding shortest path from 'kotlin' to 'native':")
val path = graph.findShortestPath("kotlin", "native")
if (path != null) {
    println("  Path: ${path.joinToString(" → ") { it.label }}")
    println("  Path length: ${path.size} steps")
} else {
    println("  No path found")
}

// Arrange in circular layout
println()
graph.arrangeInCircle()

// Show positions
println("Node positions:")
graph.getNodes().forEach { node ->
    println("  ${node.label}: ${node.position}")
}

// Export visualization
println()
println("Generating SpaceGraph visualization...")
val tempDir = System.getProperty("java.io.tmpdir")
val svgFile = java.io.File(tempDir, "spacegraph-kotlin-demo.svg")
svgFile.writeText(graph.exportToSVG())
println("✓ SpaceGraph saved to: ${svgFile.absolutePath}")

// Generate an interactive HTML version
val htmlContent = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SpaceGraph Kotlin Demo</title>
    <style>
        body { 
            margin: 0; 
            padding: 20px; 
            background: #0a0a0a; 
            color: white; 
            font-family: Arial, sans-serif; 
        }
        .container { 
            max-width: 1000px; 
            margin: 0 auto; 
        }
        .info { 
            margin-bottom: 20px; 
            padding: 15px; 
            background: #1a1a1a; 
            border-radius: 8px; 
        }
        svg { 
            border: 1px solid #333; 
            border-radius: 8px; 
            display: block; 
            margin: 0 auto; 
        }
        .path { 
            margin-top: 10px; 
            color: #00d0ff; 
            font-weight: bold; 
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🌌 SpaceGraph Kotlin Demo</h1>
        <div class="info">
            <h3>Kotlin Ecosystem Knowledge Graph</h3>
            <p><strong>Nodes:</strong> ${graph.getNodes().size} | <strong>Edges:</strong> ${graph.getEdges().size}</p>
            <div class="path">Shortest Path: ${path?.joinToString(" → ") { it.label } ?: "None"}</div>
        </div>
        ${graph.exportToSVG()}
        <div class="info">
            <p>This graph shows the relationships in the Kotlin ecosystem. Each node represents a technology or concept, and edges show how they enable or relate to each other.</p>
            <p>Generated by K2script SpaceGraph demo at ${java.time.LocalDateTime.now()}</p>
        </div>
    </div>
</body>
</html>
""".trimIndent()

val htmlFile = java.io.File(tempDir, "spacegraph-kotlin-demo.html") 
htmlFile.writeText(htmlContent)
println("✓ Interactive HTML saved to: ${htmlFile.absolutePath}")

println()
println("=== SpaceGraph Demo Complete! ===")
println("🌌 Visualization files created:")
println("   SVG: ${svgFile.absolutePath}")
println("   HTML: ${htmlFile.absolutePath}")
println()
println("Open the HTML file in your browser to see the interactive SpaceGraph! 🚀")