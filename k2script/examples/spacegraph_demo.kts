#!/usr/bin/env kotlin

// SpaceGraph Kotlin Demo - Data visualization concepts with k2script
// This demonstrates graph data structures and spatial relationships

println("=== SpaceGraph Kotlin Demo ===")
println()

// Define basic graph structures using TrikeShed-style patterns
data class Vector3D(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Double) = Vector3D(x * scalar, y * scalar, z * scalar)
    fun distance(other: Vector3D): Double = kotlin.math.sqrt((x - other.x).let { it * it } + (y - other.y).let { it * it } + (z - other.z).let { it * it })
    override fun toString() = "(${"%.2f".format(x)}, ${"%.2f".format(y)}, ${"%.2f".format(z)})"
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
    
    fun addNode(id: String, label: String, position: Vector3D = Vector3D(0.0, 0.0, 0.0), data: Map<String, Any> = emptyMap()): GraphNode {
        val node = GraphNode(id, label, position, data)
        nodes[id] = node
        println("  Added node: $id -> $label at $position")
        return node
    }
    
    fun addEdge(sourceId: String, targetId: String, weight: Double = 1.0, label: String? = null): GraphEdge? {
        val source = nodes[sourceId] ?: return null.also { println("  Error: Source node $sourceId not found") }
        val target = nodes[targetId] ?: return null.also { println("  Error: Target node $targetId not found") }
        
        val edgeId = "$sourceId-$targetId"
        val edge = GraphEdge(edgeId, source, target, weight, label)
        edges[edgeId] = edge
        println("  Added edge: $sourceId -> $targetId (weight: $weight)")
        return edge
    }
    
    fun getNodes() = nodes.values.toList()
    fun getEdges() = edges.values.toList()
    
    fun calculateGraphMetrics() {
        println("Graph Metrics:")
        println("  Nodes: ${nodes.size}")
        println("  Edges: ${edges.size}")
        
        // Calculate average node connectivity
        val connectivity = nodes.values.map { node ->
            edges.values.count { it.source == node || it.target == node }
        }
        println("  Average connectivity: ${"%.2f".format(connectivity.average())}")
        
        // Calculate graph density
        val maxPossibleEdges = nodes.size * (nodes.size - 1) / 2
        val density = if (maxPossibleEdges > 0) edges.size.toDouble() / maxPossibleEdges else 0.0
        println("  Graph density: ${"%.2f".format(density)}")
    }
    
    fun findShortestPath(startId: String, endId: String): List<GraphNode>? {
        // Simple breadth-first search for shortest path
        val start = nodes[startId] ?: return null
        val end = nodes[endId] ?: return null
        
        val queue = mutableListOf(listOf(start))
        val visited = mutableSetOf<String>()
        
        while (queue.isNotEmpty()) {
            val path = queue.removeAt(0)
            val current = path.last()
            
            if (current == end) {
                return path
            }
            
            if (current.id in visited) continue
            visited.add(current.id)
            
            // Find connected nodes
            val connectedNodes = edges.values.filter { 
                it.source == current || it.target == current 
            }.map { 
                if (it.source == current) it.target else it.source 
            }
            
            connectedNodes.forEach { neighbor ->
                if (neighbor.id !in visited) {
                    queue.add(path + neighbor)
                }
            }
        }
        
        return null // No path found
    }
    
    fun applyForceLayout(iterations: Int = 100) {
        println("Applying force-directed layout ($iterations iterations)...")
        val k = kotlin.math.sqrt(1000.0 / nodes.size) // Optimal distance between nodes
        
        repeat(iterations) { iteration ->
            val forces = mutableMapOf<String, Vector3D>()
            
            // Initialize forces
            nodes.keys.forEach { forces[it] = Vector3D(0.0, 0.0, 0.0) }
            
            // Repulsive forces between all nodes
            nodes.values.forEach { node1 ->
                nodes.values.forEach { node2 ->
                    if (node1 != node2) {
                        val delta = node1.position - node2.position
                        val distance = delta.distance(Vector3D(0.0, 0.0, 0.0)).coerceAtLeast(0.1)
                        val repulsion = delta * (k * k / distance / distance * 0.5)
                        forces[node1.id] = forces[node1.id]!! + repulsion
                    }
                }
            }
            
            // Attractive forces for connected nodes
            edges.values.forEach { edge ->
                val delta = edge.target.position - edge.source.position
                val distance = delta.distance(Vector3D(0.0, 0.0, 0.0))
                val attraction = delta * (distance * distance / k * 0.1)
                
                forces[edge.source.id] = forces[edge.source.id]!! + attraction
                forces[edge.target.id] = forces[edge.target.id]!! - attraction
            }
            
            // Apply forces with damping
            val damping = 0.9
            nodes.values.forEach { node ->
                val force = forces[node.id]!! * damping
                node.position = node.position + force * 0.01
            }
            
            // Progress update
            if (iteration % 20 == 0) {
                print(".")
            }
        }
        println(" Done!")
    }
    
    fun exportToSVG(): String {
        val width = 800
        val height = 600
        val svg = StringBuilder()
        
        svg.append("""<svg width="$width" height="$height" xmlns="http://www.w3.org/2000/svg">""")
        svg.append("""<rect width="100%" height="100%" fill="black"/>""")
        
        // Scale and center positions
        val positions = nodes.values.map { it.position }
        val minX = positions.minOfOrNull { it.x } ?: 0.0
        val maxX = positions.maxOfOrNull { it.x } ?: 0.0
        val minY = positions.minOfOrNull { it.y } ?: 0.0
        val maxY = positions.maxOfOrNull { it.y } ?: 0.0
        
        val scaleX = if (maxX != minX) (width - 100) / (maxX - minX) else 1.0
        val scaleY = if (maxY != minY) (height - 100) / (maxY - minY) else 1.0
        val scale = kotlin.math.min(scaleX, scaleY)
        
        // Draw edges
        edges.values.forEach { edge ->
            val x1 = (edge.source.position.x - minX) * scale + 50
            val y1 = (edge.source.position.y - minY) * scale + 50
            val x2 = (edge.target.position.x - minX) * scale + 50
            val y2 = (edge.target.position.y - minY) * scale + 50
            
            svg.append("""<line x1="$x1" y1="$y1" x2="$x2" y2="$y2" stroke="#00d0ff" stroke-width="2"/>""")
        }
        
        // Draw nodes
        nodes.values.forEach { node ->
            val x = (node.position.x - minX) * scale + 50
            val y = (node.position.y - minY) * scale + 50
            
            svg.append("""<circle cx="$x" cy="$y" r="8" fill="#ff6b6b" stroke="#fff" stroke-width="2"/>""")
            svg.append("""<text x="$x" y="${y-15}" text-anchor="middle" fill="white" font-size="12">${node.label}</text>""")
        }
        
        svg.append("</svg>")
        return svg.toString()
    }
}

// Create a sample graph
println("Creating sample SpaceGraph...")
val graph = SpaceGraphKt()

// Add nodes for a simple knowledge graph
val concepts = listOf(
    "kotlin" to Vector3D(0.0, 0.0, 0.0),
    "jvm" to Vector3D(100.0, 0.0, 0.0),
    "android" to Vector3D(50.0, 100.0, 0.0),
    "coroutines" to Vector3D(-50.0, 50.0, 0.0),
    "multiplatform" to Vector3D(0.0, -100.0, 0.0),
    "javascript" to Vector3D(-100.0, -50.0, 0.0),
    "native" to Vector3D(150.0, 50.0, 0.0)
)

concepts.forEach { (label, pos) ->
    graph.addNode(label, label.capitalize(), pos, mapOf("type" to "concept"))
}

// Add relationships
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

println()
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
    println("  Path: ${path.joinToString(" -> ") { it.label }}")
} else {
    println("  No path found")
}

// Apply force-directed layout
println()
graph.applyForceLayout(50)

// Show final positions
println()
println("Final node positions:")
graph.getNodes().forEach { node ->
    println("  ${node.label}: ${node.position}")
}

// Export visualization
println()
println("Exporting graph to SVG...")
val tempDir = System.getProperty("java.io.tmpdir")
val svgFile = java.io.File(tempDir, "spacegraph-demo.svg")
svgFile.writeText(graph.exportToSVG())
println("Graph saved to: ${svgFile.absolutePath}")

// System info for SpaceGraph context
println()
println("=== SpaceGraph Environment ===")
println("Java version: ${System.getProperty("java.version")}")
println("Available memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB")
println("Processors: ${Runtime.getRuntime().availableProcessors()}")

println()
println("SpaceGraph Kotlin demo complete! 🌌")
println("Open ${svgFile.absolutePath} in a browser to view the graph visualization.")