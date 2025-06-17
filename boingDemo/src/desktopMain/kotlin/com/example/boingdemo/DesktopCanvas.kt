package com.example.boingdemo

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
actual fun EconoCanvas.drawOval(x: Float, y: Float, w: Float, h: Float, color: Int) {
    paint.color = color
    this.drawOval(Rect.makeXYWH(x, y, w, h), paint)
}
actual fun EconoCanvas.drawArc(x: Float, y: Float, r: Float, startAngle: Float, sweepAngle: Float, color: Int) {
    paint.color = color
    this.drawArc(x - r, y - r, x + r, y + r, startAngle, sweepAngle, true, paint)
}

// This main function will serve the JVM entry point.
// For Native targets, a main function in each nativeMain (e.g., linuxX64Main) might be needed
// or the build system configured to use this one if possible.
// The compose plugin often helps in setting up native entry points.
fun main() { // This will be the JVM entry point.
    runBoingDemo { onFrame ->
        var lastTime = getTimeNanos()
        SkiaWindow(title = "Boing Ball (Desktop)").apply {
            // Corrected Skiko API for defaultRenderer:
            defaultRenderer = object : Renderer {
                override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                     // val currentTime = getTimeNanos() // Not needed, nanoTime is provided
                     val dt = (nanoTime - lastTime) / 1_000_000_000.0f
                     lastTime = nanoTime // Update lastTime with the nanoTime from the current frame
                     onFrame(canvas, width, height, dt)
                }
            }
            // TODO: The SkiaWindow needs to be made visible.
            // This usually happens by it being the last expression in main,
            // or by explicitly calling something like 'window.show()' if the API requires,
            // or by it being part of a Compose Application block.
            // For SkiaWindow directly, it might start itself. If not, this will be a silent exit.
            // The original user code just had 'SkiaWindow().apply { ... }'
            // which might be okay if SkiaWindow's constructor or apply block handles visibility.
        }
    }
}

// --- Audio Implementation ---
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import java.io.BufferedInputStream
import java.io.InputStream

// Simple cache for clips
private val clipCache = mutableMapOf<String, Clip>()
// Use a map to track if the initial loading warning for a specific sound path has been printed.
private val soundLoadAttemptedOrWarned = mutableMapOf<String, Boolean>()

actual fun playSound(filePath: String) {
    try {
        val clip = clipCache.getOrPut(filePath) {
            val resourceStream: InputStream? = Thread.currentThread().contextClassLoader.getResourceAsStream(filePath)
            if (resourceStream == null) {
                if (soundLoadAttemptedOrWarned[filePath] != true) {
                    println("Warning: Audio resource not found: $filePath")
                    soundLoadAttemptedOrWarned[filePath] = true
                }
                throw Exception("Audio resource not found: $filePath")
            }
            val audioInputStream = AudioSystem.getAudioInputStream(BufferedInputStream(resourceStream))
            AudioSystem.getClip().also { it.open(audioInputStream) }
        }

        if (clip.isRunning) {
            clip.stop() // Stop previous playback
        }
        clip.framePosition = 0 // Rewind to start
        clip.start()
        soundLoadAttemptedOrWarned[filePath] = true // Mark as successfully loaded and played at least once
    } catch (e: Exception) {
        if (soundLoadAttemptedOrWarned[filePath] != true) {
            println("Warning: Could not play sound $filePath on JVM: ${e.message}")
            soundLoadAttemptedOrWarned[filePath] = true // Mark as 'attempted' to avoid repeated full errors for this path
        }
         // Fallback or ignore if sound can't be played
    }
}
