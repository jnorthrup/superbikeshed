package com.moneyfan.demo.visualization

import com.moneyfan.demo.state.AppState
import org.w3c.dom.HTMLDivElement
import three.js.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class VisualizationManager(
    internal val container: HTMLDivElement,
    internal val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    internal var scene: Scene? = null
    internal var camera: PerspectiveCamera? = null
    internal var renderer: WebGLRenderer? = null
    internal var currentVisualization: Visualization? = null
    internal var controls: OrbitControls? = null

    // State flow for portfolio updates
    internal val _portfolioState = MutableStateFlow<PortfolioState?>(null)
    val portfolioState = _portfolioState.asStateFlow()

    fun initializeVisualization(state: AppState) {
        scope.launch {
            setupThreeJS()
            setupControls()
            updateVisualization(state)
        }
    }

    fun updateVisualization(state: AppState) {
        scope.launch {
            currentVisualization?.dispose()
            currentVisualization = when (state.viewMode) {
                ViewMode.CANDLESTICK -> CandlestickVisualization(scene!!, state)
                ViewMode.LINE -> LineVisualization(scene!!, state)
                ViewMode.AREA -> AreaVisualization(scene!!, state)
                ViewMode.VOLUME -> VolumeVisualization(scene!!, state)
                ViewMode.HEATMAP -> HeatmapVisualization(scene!!, state)
            }
            currentVisualization?.initialize()
            updatePortfolioState(state)
            animate()
        }
    }

    internal fun setupThreeJS() {
        // Create scene with dark theme
        scene = Scene().apply {
            background = Color(0x1a1a1a)
            fog = FogExp2(0x1a1a1a, 0.0025)
        }

        // Create camera with better initial position
        camera = PerspectiveCamera(
            60.0,
            container.clientWidth.toDouble() / container.clientHeight.toDouble(),
            0.1,
            1000.0
        ).apply {
            position.set(5.0, 5.0, 5.0)
            lookAt(0.0, 0.0, 0.0)
        }

        // Create renderer with antialiasing
        renderer = WebGLRenderer(WebGLRendererParameters(
            antialias = true,
            alpha = true
        )).apply {
            setSize(container.clientWidth, container.clientHeight)
            setPixelRatio(window.devicePixelRatio)
            container.appendChild(domElement)
        }

        // Add ambient and directional lights
        scene?.add(AmbientLight(0xffffff, 0.5))
        scene?.add(DirectionalLight(0xffffff, 0.8).apply {
            position.set(1.0, 1.0, 1.0)
            castShadow = true
        })
    }

    internal fun setupControls() {
        controls = OrbitControls(camera!!, renderer!!.domElement).apply {
            enableDamping = true
            dampingFactor = 0.05
            screenSpacePanning = false
            minDistance = 1.0
            maxDistance = 50.0
            maxPolarAngle = Math.PI / 2
        }
    }

    internal fun updatePortfolioState(state: AppState) {
        state.data?.let { data ->
            val portfolioValue = data.candles.lastOrNull()?.close ?: 0.0
            val returns = calculateReturns(data.candles)
            val volatility = calculateVolatility(returns)
            val sharpeRatio = calculateSharpeRatio(returns)

            _portfolioState.value = PortfolioState(
                value = portfolioValue,
                returns = returns,
                volatility = volatility,
                sharpeRatio = sharpeRatio
            )
        }
    }

    internal fun calculateReturns(candles: List<Candle>): List<Double> {
        return candles.zipWithNext { a, b -> (b.close - a.close) / a.close }
    }

    internal fun calculateVolatility(returns: List<Double>): Double {
        val mean = returns.average()
        return kotlin.math.sqrt(returns.map { (it - mean) * (it - mean) }.average())
    }

    internal fun calculateSharpeRatio(returns: List<Double>): Double {
        val mean = returns.average()
        val volatility = calculateVolatility(returns)
        return if (volatility > 0) mean / volatility else 0.0
    }

    internal fun animate() {
        requestAnimationFrame { animate() }
        controls?.update()
        currentVisualization?.update()
        renderer?.render(scene!!, camera!!)
    }
}

data class PortfolioState(
    val value: Double,
    val returns: List<Double>,
    val volatility: Double,
    val sharpeRatio: Double
)

interface Visualization {
    fun initialize()
    fun update()
    fun dispose()
} 