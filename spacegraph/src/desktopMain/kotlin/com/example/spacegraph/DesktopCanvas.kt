package com.example.spacegraph

import org.jetbrains.skia.*
import org.jetbrains.skiko.SkiaWindow
import org.jetbrains.skiko.Renderer
import kotlin.system.getTimeNanos

actual typealias EconoCanvas = org.jetbrains.skia.Canvas
private val paint = Paint()

actual fun EconoCanvas.save() { this.save() }
actual fun EconoCanvas.restore() { this.restore() }
actual fun EconoCanvas.translate(x: Float, y: Float) { this.translate(x, y) }
actual fun EconoCanvas.rotate(degrees: Float) { this.rotate(degrees) }

actual fun EconoCanvas.clear(color: Int) { this.clear(color) }
actual fun EconoCanvas.drawNode(x: Float, y: Float, radius: Float, color: Int) {
    paint.color = color
    this.drawCircle(x, y, radius, paint)
}
actual fun EconoCanvas.drawEdge(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
    paint.color = color
    paint.strokeWidth = 4f
    paint.mode = PaintMode.STROKE
    this.drawLine(x1, y1, x2, y2, paint)
    paint.mode = PaintMode.FILL // Reset
}

fun main() {
    runSpaceGraphDemo { onFrame ->
        var lastTime = getTimeNanos()
        SkiaWindow(title = "SpaceGraph (Desktop)").apply {
            defaultRenderer = object : Renderer {
                override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                    val dt = (nanoTime - lastTime) / 1_000_000_000.0f
                    lastTime = nanoTime
                    onFrame(canvas, width, height, dt)
                }
            }
        }
    }
} 