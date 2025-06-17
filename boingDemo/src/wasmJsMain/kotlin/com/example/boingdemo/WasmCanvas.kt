package com.example.boingdemo

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI

actual typealias EconoCanvas = CanvasRenderingContext2D
private fun toCssColor(color: Int) = "#${(color and 0xFFFFFF).toString(16).padStart(6, '0')}"

actual fun EconoCanvas.save() { this.save() }
actual fun EconoCanvas.restore() { this.restore() }
actual fun EconoCanvas.translate(x: Float, y: Float) { this.translate(x.toDouble(), y.toDouble()) }
actual fun EconoCanvas.rotate(degrees: Float) { this.rotate(degrees * PI / 180.0) }

actual fun EconoCanvas.clear(color: Int) {
    save(); this.fillStyle = toCssColor(color); fillRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble()); restore()
}
actual fun EconoCanvas.drawOval(x: Float, y: Float, w: Float, h: Float, color: Int) {
    fillStyle = toCssColor(color); beginPath(); ellipse(x + w/2.0, y + h/2.0, w/2.0, h/2.0, 0.0, 0.0, 2*PI); fill()
}
actual fun EconoCanvas.drawArc(x: Float, y: Float, r: Float, startAngle: Float, sweepAngle: Float, color: Int) {
    val startRad = startAngle * PI / 180.0; val endRad = (startAngle + sweepAngle) * PI / 180.0
    fillStyle = toCssColor(color); beginPath(); moveTo(x.toDouble(), y.toDouble()); arc(x.toDouble(), y.toDouble(), r.toDouble(), startRad, endRad); closePath(); fill()
}

fun main() {
    runBoingDemo { onFrame ->
        val canvasEl = document.getElementById("kmp-canvas") as HTMLCanvasElement
        val context = canvasEl.getContext("2d") as EconoCanvas
        fun onResize() { canvasEl.width = window.innerWidth; canvasEl.height = window.innerHeight }
        window.onresize = { onResize(); null }; onResize()

        var lastTime = window.performance.now()
        fun render(time: Double) {
            val dt = ((time - lastTime) / 1000.0).toFloat()
            lastTime = time
            onFrame(context, canvasEl.width, canvasEl.height, dt)
            window.requestAnimationFrame(::render)
        }
        window.requestAnimationFrame(::render)
    }
}

// --- Audio Implementation ---
private val audioCache = mutableMapOf<String, org.w3c.dom.Audio>()

actual fun playSound(filePath: String) {
    try {
        val sound = audioCache.getOrPut(filePath) {
            org.w3c.dom.Audio(filePath) // Assumes filePath is relative to where index.html is served
        }
        sound.play().catch { err -> console.error("Error playing sound $filePath: $err") }
    } catch (e: Exception) {
        console.error("Exception during sound playback setup for $filePath: ${e.message}")
    }
}
