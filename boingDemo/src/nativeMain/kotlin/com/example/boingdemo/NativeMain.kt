package com.example.boingdemo

// Stub implementation for Native canvas operations
// Full implementation would require C interop or a graphics library
class NativeCanvas

actual typealias EconoCanvas = NativeCanvas

actual fun EconoCanvas.save() { /* TODO: Implement native save */ }
actual fun EconoCanvas.restore() { /* TODO: Implement native restore */ }
actual fun EconoCanvas.translate(x: Float, y: Float) { /* TODO: Implement native translate */ }
actual fun EconoCanvas.rotate(degrees: Float) { /* TODO: Implement native rotate */ }

actual fun EconoCanvas.clear(color: Int) { /* TODO: Implement native clear */ }
actual fun EconoCanvas.drawOval(x: Float, y: Float, w: Float, h: Float, color: Int) { /* TODO: Implement native drawOval */ }
actual fun EconoCanvas.drawArc(x: Float, y: Float, r: Float, startAngle: Float, sweepAngle: Float, color: Int) { /* TODO: Implement native drawArc */ }

fun main() {
    println("BoingDemo Native - Stub implementation (graphics operations not implemented)")
    runBoingDemo { onFrame ->
        // TODO: Set up native graphics rendering
        val canvas = NativeCanvas()
        onFrame(canvas, 800, 600, 0.016f) // Simulate 60fps
    }
}

// --- Audio Implementation (Stub) ---
// Stub implementation for Native audio for now
// Full implementation would require C interop (e.g., with OpenAL, miniaudio) or a KMP library.
private var nativeSoundWarningPrinted = false
actual fun playSound(filePath: String) {
    if (!nativeSoundWarningPrinted) {
        println("Native audio playback for '$filePath' is not yet implemented. Bounce occurred.")
        nativeSoundWarningPrinted = true
    }
}
