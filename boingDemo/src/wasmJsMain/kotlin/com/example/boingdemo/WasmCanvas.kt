package com.example.boingdemo

import kotlin.math.PI

// External declarations for browser APIs
external class HTMLCanvasElement {
    var width: Int
    var height: Int
    fun getContext(contextId: String): CanvasRenderingContext2D?
}

external class CanvasRenderingContext2D {
    var fillStyle: String
    val canvas: HTMLCanvasElement
    fun save()
    fun restore()
    fun translate(x: Double, y: Double)
    fun rotate(angle: Double)
    fun fillRect(x: Double, y: Double, width: Double, height: Double)
    fun beginPath()
    fun ellipse(x: Double, y: Double, radiusX: Double, radiusY: Double, rotation: Double, startAngle: Double, endAngle: Double)
    fun fill()
    fun moveTo(x: Double, y: Double)
    fun arc(x: Double, y: Double, radius: Double, startAngle: Double, endAngle: Double)
    fun closePath()
}

external object document {
    fun getElementById(id: String): HTMLCanvasElement?
}

external object window {
    var innerWidth: Int
    var innerHeight: Int
    var onresize: (() -> Unit)?
    val performance: Performance
    fun requestAnimationFrame(callback: (Double) -> Unit)
}

external class Performance {
    fun now(): Double
}

external class Audio(src: String) {
    fun play()
}

external object console {
    fun error(message: String)
}

actual typealias EconoCanvas = CanvasRenderingContext2D

internal fun toCssColor(color: Int) = "#${(color and 0xFFFFFF).toString(16).padStart(6, '0')}"

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
        val canvasEl = document.getElementById("kmp-canvas") ?: return@runBoingDemo
        val context = canvasEl.getContext("2d") ?: return@runBoingDemo
        fun onResize() { canvasEl.width = window.innerWidth; canvasEl.height = window.innerHeight }
        window.onresize = { onResize() }; onResize()

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
internal val audioCache = mutableMapOf<String, Audio>()

actual fun playSound(filePath: String) {
    try {
        val sound = audioCache.getOrPut(filePath) {
            Audio(filePath) // Assumes filePath is relative to where index.html is served
        }
        sound.play()
    } catch (e: Exception) {
        console.error("Exception during sound playback setup for $filePath: ${e.message}")
    }
}