package com.example.spacegraph

expect class EconoCanvas

expect fun EconoCanvas.save()
expect fun EconoCanvas.restore()
expect fun EconoCanvas.translate(x: Float, y: Float)
expect fun EconoCanvas.rotate(degrees: Float)

expect fun EconoCanvas.clear(color: Int)
expect fun EconoCanvas.drawNode(x: Float, y: Float, radius: Float, color: Int)
expect fun EconoCanvas.drawEdge(x1: Float, y1: Float, x2: Float, y2: Float, color: Int)

const val NODE_COLOR = 0xFF00FF00.toInt()
const val EDGE_COLOR = 0xFF0000FF.toInt()
const val BG_COLOR = 0xFF222222.toInt()

fun runSpaceGraphDemo(getFrameLoop: (onFrame: (canvas: EconoCanvas, w: Int, h: Int, dt: Float) -> Unit) -> Unit) {
    getFrameLoop { canvas, width, height, dt ->
        canvas.clear(BG_COLOR)
        // Draw a simple graph: two nodes and an edge
        val x1 = width * 0.3f
        val y1 = height * 0.5f
        val x2 = width * 0.7f
        val y2 = height * 0.5f
        canvas.drawEdge(x1, y1, x2, y2, EDGE_COLOR)
        canvas.drawNode(x1, y1, 30f, NODE_COLOR)
        canvas.drawNode(x2, y2, 30f, NODE_COLOR)
    }
} 