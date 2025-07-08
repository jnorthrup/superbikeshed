package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import three.js.*
import kotlin.math.max
import kotlin.math.min

class AreaVisualization(
    internal val scene: Scene,
    internal val state: AppState
) : Visualization {
    internal val area = mutableListOf<Object3D>()
    internal val material = MeshPhongMaterial().apply {
        color = Color(0x00ff00)
        transparent = true
        opacity = 0.5
    }

    override fun initialize() {
        state.data?.candles?.let { candles ->
            val points = mutableListOf<Vector3>()
            val maxPrice = candles.maxOf { it.high }
            val minPrice = candles.minOf { it.low }
            val priceRange = maxPrice - minPrice

            // Create top points
            candles.forEachIndexed { index, candle ->
                val x = index * 0.15f
                val y = ((candle.close - minPrice) / priceRange * 5.0f).toFloat()
                points.add(Vector3(x, y, 0.0f))
            }

            // Create bottom points (base line)
            candles.reversed().forEachIndexed { index, _ ->
                val x = (candles.size - 1 - index) * 0.15f
                points.add(Vector3(x, 0.0f, 0.0f))
            }

            val geometry = ShapeGeometry(Shape().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until candles.size) {
                    lineTo(points[i].x, points[i].y)
                }
                for (i in candles.size until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
                closePath()
            })

            val mesh = Mesh(geometry, material)
            scene.add(mesh)
            area.add(mesh)

            // Add grid
            val gridHelper = GridHelper(10, 10)
            scene.add(gridHelper)
        }
    }

    override fun update() {
        // Animate area if needed
        area.forEach { it.rotation.y += 0.01 }
    }

    override fun dispose() {
        area.forEach { scene.remove(it) }
        area.clear()
    }
} 