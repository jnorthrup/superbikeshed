package com.example.boingdemo

import kotlin.math.PI

expect class EconoCanvas

expect fun EconoCanvas.save()
expect fun EconoCanvas.restore()
expect fun EconoCanvas.translate(x: Float, y: Float)
expect fun EconoCanvas.rotate(degrees: Float)

expect fun EconoCanvas.clear(color: Int)
expect fun EconoCanvas.drawOval(x: Float, y: Float, w: Float, h: Float, color: Int)
expect fun EconoCanvas.drawArc(x: Float, y: Float, r: Float, startAngle: Float, sweepAngle: Float, color: Int)

const val RED = 0xFFFF0000.toInt()
const val WHITE = 0xFFFFFFFF.toInt()
const val BLUE = 0xFF000080.toInt()
const val SHADOW_COLOR = 0x80000000.toInt()

const val BOUNCE_SOUND_PATH = "bounce.wav" // Define sound path once
expect fun playSound(filePath: String)

data class BoingBall(
    var x: Float, var y: Float, val radius: Float,
    var vx: Float = 250f, var vy: Float = 200f,
    var rotation: Float = 0f, private val rotSpeed: Float = 180f,
    var justBounced: Boolean = false // New property
)

fun BoingBall.update(dt: Float, width: Int, height: Int) {
    justBounced = false // Reset at start of update
    x += vx * dt
    y += vy * dt
    rotation += rotSpeed * dt

    if ((x < radius && vx < 0) || (x > width - radius && vx > 0)) {
        vx *= -1
        justBounced = true
    }
    if ((y < radius && vy < 0) || (y > height - radius && vy > 0)) {
        vy *= -1
        justBounced = true
    }
}

fun BoingBall.draw(canvas: EconoCanvas, width: Int, height: Int) {
    val shadowY = (height - radius * 0.8f)
    val shadowHeight = radius / 2.5f
    val shadowWidthFactor = 1.0f - (y / height).coerceIn(0f, 1f) * 0.5f
    canvas.drawOval(x - radius * shadowWidthFactor, shadowY, radius * 2 * shadowWidthFactor, shadowHeight, SHADOW_COLOR)

    canvas.save()
    canvas.translate(x, y)
    canvas.rotate(rotation)

    for (i in 0 until 8) {
        val color = if (i % 2 == 0) RED else WHITE
        canvas.drawArc(0f, 0f, radius, i * 45f, 45f, color)
    }
    canvas.restore()
}

fun runBoingDemo(getFrameLoop: (onFrame: (canvas: EconoCanvas, w: Int, h: Int, dt: Float) -> Unit) -> Unit) {
    val ball = BoingBall(100f, 100f, 50f)
    // var soundLoaded = false // Track if sound setup was successful for relevant platforms - platform actuals will handle this
    // Initial sound loading/setup might happen here or in platform-specific main

    getFrameLoop { canvas, width, height, dt ->
        ball.update(dt.coerceAtMost(0.032f), width, height)
        canvas.clear(BLUE)
        ball.draw(canvas, width, height)
        if (ball.justBounced) {
            playSound(BOUNCE_SOUND_PATH)
        }
    }
}
