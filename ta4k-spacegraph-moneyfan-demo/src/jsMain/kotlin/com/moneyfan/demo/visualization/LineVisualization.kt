package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import three.js.*
import kotlin.math.max
import kotlin.math.min

class LineVisualization(
    internal val scene: Scene,
    internal val state: AppState
) : Visualization {
    internal val line = mutableListOf<Object3D>()
    internal val material = LineBasicMaterial().apply {
        color = Color(0x00ff00)
        linewidth = 2.0
    }

    override fun initialize() {
        state.data?.candles?.let { candles ->
            val points = mutableListOf<Vector3>()
            val maxPrice = candles.maxOf { it.high }
            val minPrice = candles.minOf { it.low }
            val priceRange = maxPrice - minPrice

            candles.forEachIndexed { index, candle ->
                val x = index * 0.15f
                val y = ((candle.close - minPrice) / priceRange * 5.0f).toFloat()
                points.add(Vector3(x, y, 0.0f))
            }

            val geometry = BufferGeometry().apply {
                setFromPoints(points)
            }

            val lineMesh = Line(geometry, material)
            scene.add(lineMesh)
            line.add(lineMesh)

            // Add grid
            val gridHelper = GridHelper(10, 10)
            scene.add(gridHelper)
        }
    }

    override fun update() {
        // Animate line if needed
        line.forEach { it.rotation.y += 0.01 }
    }

    override fun dispose() {
        line.forEach { scene.remove(it) }
        line.clear()
    }
} 