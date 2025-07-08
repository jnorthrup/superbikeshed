package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import three.js.*
import kotlin.math.max
import kotlin.math.min

class HeatmapVisualization(
    internal val scene: Scene,
    internal val state: AppState
) : Visualization {
    internal val heatmap = mutableListOf<Object3D>()
    internal val material = MeshPhongMaterial().apply {
        color = Color(0x00ff00)
        transparent = true
        opacity = 0.7
    }

    override fun initialize() {
        state.data?.candles?.let { candles ->
            val maxPrice = candles.maxOf { it.high }
            val minPrice = candles.minOf { it.low }
            val priceRange = maxPrice - minPrice
            val maxVolume = candles.maxOf { it.volume }
            val minVolume = candles.minOf { it.volume }
            val volumeRange = maxVolume - minVolume

            // Create a grid of cells
            val gridSize = 10
            val cellSize = 0.2f
            val gridWidth = gridSize * cellSize

            for (i in 0 until gridSize) {
                for (j in 0 until gridSize) {
                    val candleIndex = (i * gridSize + j).coerceAtMost(candles.size - 1)
                    val candle = candles[candleIndex]
                    
                    // Calculate intensity based on price and volume
                    val priceIntensity = ((candle.close - minPrice) / priceRange).toFloat()
                    val volumeIntensity = ((candle.volume - minVolume) / volumeRange).toFloat()
                    val intensity = (priceIntensity + volumeIntensity) / 2

                    val geometry = BoxGeometry(cellSize, 0.1, cellSize)
                    val cellMaterial = material.clone().apply {
                        color = Color().setHSL(intensity * 0.3, 1.0, 0.5)
                    }

                    val cell = Mesh(geometry, cellMaterial).apply {
                        position.x = (i - gridSize / 2) * cellSize
                        position.z = (j - gridSize / 2) * cellSize
                        position.y = intensity * 2
                    }

                    scene.add(cell)
                    heatmap.add(cell)
                }
            }

            // Add grid
            val gridHelper = GridHelper(gridWidth, gridSize)
            scene.add(gridHelper)
        }
    }

    override fun update() {
        // Animate heatmap if needed
        heatmap.forEach { it.rotation.y += 0.01 }
    }

    override fun dispose() {
        heatmap.forEach { scene.remove(it) }
        heatmap.clear()
    }
} 