package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import three.js.*
import kotlin.math.max
import kotlin.math.min

class CandlestickVisualization(
    private val scene: Scene,
    private val state: AppState
) : Visualization {
    private val candles = mutableListOf<Object3D>()
    private val material = MeshPhongMaterial()
    private val geometry = BoxGeometry(0.1, 0.1, 0.1)

    override fun initialize() {
        state.data?.candles?.forEachIndexed { index, candle ->
            val height = (candle.high - candle.low).toFloat()
            val bodyHeight = (candle.close - candle.open).toFloat()
            val isGreen = candle.close > candle.open

            // Create candlestick body
            val body = Mesh(
                BoxGeometry(0.08, bodyHeight, 0.08),
                MeshPhongMaterial().apply {
                    color = if (isGreen) Color(0x00ff00) else Color(0xff0000)
                }
            ).apply {
                position.x = index * 0.15f
                position.y = (candle.open + candle.close) / 2
            }

            // Create wick
            val wick = Mesh(
                BoxGeometry(0.02, height, 0.02),
                MeshPhongMaterial().apply {
                    color = if (isGreen) Color(0x00ff00) else Color(0xff0000)
                }
            ).apply {
                position.x = index * 0.15f
                position.y = (candle.high + candle.low) / 2
            }

            scene.add(body)
            scene.add(wick)
            candles.add(body)
            candles.add(wick)
        }

        // Add grid
        val gridHelper = GridHelper(10, 10)
        scene.add(gridHelper)
    }

    override fun update() {
        // Animate candlesticks if needed
        candles.forEach { candle ->
            candle.rotation.y += 0.01
        }
    }

    override fun dispose() {
        candles.forEach { scene.remove(it) }
        candles.clear()
    }
} 