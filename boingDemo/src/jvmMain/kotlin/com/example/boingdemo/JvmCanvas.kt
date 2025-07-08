package com.example.boingdemo

// JVM stub implementation without external dependencies
// For a full implementation, you would need a graphics library like Skia or Compose Desktop

class JvmCanvas

actual typealias EconoCanvas = JvmCanvas

actual fun EconoCanvas.save() { /* TODO: Implement JVM save */ }
actual fun EconoCanvas.restore() { /* TODO: Implement JVM restore */ }
actual fun EconoCanvas.translate(x: Float, y: Float) { /* TODO: Implement JVM translate */ }
actual fun EconoCanvas.rotate(degrees: Float) { /* TODO: Implement JVM rotate */ }

actual fun EconoCanvas.clear(color: Int) { /* TODO: Implement JVM clear */ }
actual fun EconoCanvas.drawOval(x: Float, y: Float, w: Float, h: Float, color: Int) { /* TODO: Implement JVM drawOval */ }
actual fun EconoCanvas.drawArc(x: Float, y: Float, r: Float, startAngle: Float, sweepAngle: Float, color: Int) { /* TODO: Implement JVM drawArc */ }

fun main() {
    println("BoingDemo JVM - Stub implementation (graphics operations not implemented)")
    runBoingDemo { onFrame ->
        // TODO: Set up JVM graphics rendering
        val canvas = JvmCanvas()
        onFrame(canvas, 800, 600, 0.016f) // Simulate 60fps
    }
}

// --- Audio Implementation (Stub) ---
actual fun playSound(filePath: String) {
    println("Playing sound: $filePath (JVM audio not implemented)")
}