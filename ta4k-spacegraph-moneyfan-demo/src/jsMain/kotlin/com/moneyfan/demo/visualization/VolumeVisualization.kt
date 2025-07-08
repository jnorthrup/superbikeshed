package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import three.js.*
import kotlin.math.max
import kotlin.math.min

class VolumeVisualization(
    internal val scene: Scene,
    internal val state: AppState
) : Visualization {
    internal val volumes = mutableListOf<Object3D>()
    internal val material = MeshPhongMaterial().apply {
        color = Color(0x00ff00)
        transparent = true
        opacity = 0.7
    }

    override fun initialize() {
        state.data?.candles?.let { candles ->
            val maxVolume = candles.maxOf { it.volume }
            val minVolume = candles.minOf { it.volume }
            val volumeRange = maxVolume - minVolume

            candles.forEachIndexed { index, candle ->
                val height = ((candle.volume - minVolume) / volumeRange * 5.0f).toFloat()
                val isGreen = candle.close > candle.open

                val geometry = BoxGeometry(0.1, height, 0.1)
                val volumeMaterial = material.clone().apply {
                    color = if (isGreen) Color(0x00ff00) else Color(0xff0000)
                }

                val volume = Mesh(geometry, volumeMaterial).apply {
                    position.x = index * 0.15f
                    position.y = height / 2
                }

                scene.add(volume)
                volumes.add(volume)
            }

            // Add grid
            val gridHelper = GridHelper(10, 10)
            scene.add(gridHelper)
        }
    }

    override fun update() {
        // Animate volumes if needed
        volumes.forEach { it.rotation.y += 0.01 }
    }

    override fun dispose() {
        volumes.forEach { scene.remove(it) }
        volumes.clear()
    }
} 