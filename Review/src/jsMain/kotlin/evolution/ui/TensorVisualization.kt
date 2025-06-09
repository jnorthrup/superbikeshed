package evolution.ui

import borg.trikeshed.lib.*
import core.*
import evolution.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.w3c.dom.*
import kotlin.js.*
import kotlin.jvm.*
import kotlin.math.*

/**
 * Join/Series/Tensor Visualization Components
 * 
 * Real-time visualization of tensor operations using Join<A,B> patterns
 * integrated with live trading data streams.
 */

// ═══════════════════════════════════════════════════════════════════════════════════════
// TENSOR CHART COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Live tensor chart using SVG and Join operations
 */
class TensorChart : ReactComponent<TensorChart.Props, TensorChart.State>() {
    
    data class Props(
        val width: Int = 400,
        val height: Int = 200,
        val series: List<Double> = emptyList(),
        val tensorData: dynamic = null,
        val joinOperations: List<JoinOp> = emptyList(),
        val updateInterval: Int = 1000
    )
    
    data class State(
        val renderMode: RenderMode = RenderMode.LINE,
        val zoom: Double = 1.0,
        val pan: Pair<Double, Double> = 0.0 to 0.0,
        val selectedPoint: Int? = null,
        val animationFrame: Int = 0
    )
    
    enum class RenderMode { LINE, CANDLESTICK, HEATMAP, TENSOR_3D, JOIN_FLOW }
    
    data class JoinOp(
        val type: String, // "zip", "combine", "broadcast", "reduce"
        val left: String,
        val right: String,
        val result: String,
        val timestamp: Long
    )
    
    override fun createInitialState() = State()
    
    override fun onMount(props: Props, state: State, setState: (State) -> Unit) {
        // Start animation loop for live updates
        startAnimationLoop(setState, state)
    }
    
    override fun render(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "tensor-chart-container"
            style = jsObject {
                width = "${props.width}px"
                height = "${props.height}px"
                position = "relative"
                background = "#0d1117"
                border = "1px solid #30363d"
                borderRadius = "6px"
                overflow = "hidden"
            }
        },
            // Chart Controls
            renderChartControls(props, state),
            
            // Main Chart SVG
            createElement("svg", jsObject {
                width = props.width
                height = props.height - 30 // Account for controls
                style = jsObject {
                    position = "absolute"
                    top = "30px"
                    left = "0"
                }
                onMouseMove = { event -> handleMouseMove(event, props, state) }
                onClick = { event -> handleClick(event, props, state) }
            },
                when (state.renderMode) {
                    RenderMode.LINE -> renderLineSeries(props, state)
                    RenderMode.CANDLESTICK -> renderCandlesticks(props, state)
                    RenderMode.HEATMAP -> renderHeatmap(props, state)
                    RenderMode.TENSOR_3D -> render3DTensor(props, state)
                    RenderMode.JOIN_FLOW -> renderJoinFlow(props, state)
                }
            ),
            
            // Join Operations Overlay
            if (props.joinOperations.isNotEmpty()) {
                renderJoinOperations(props, state)
            } else null
        )
    }
    
    private fun renderChartControls(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "chart-controls"
            style = jsObject {
                height = "30px"
                background = "#161b22"
                display = "flex"
                alignItems = "center"
                gap = "8px"
                padding = "0 10px"
                fontSize = "10px"
            }
        },
            // Render Mode Selector
            *RenderMode.values().map { mode ->
                createElement("button", jsObject {
                    key = mode.name
                    onClick = { /* setState with new render mode */ }
                    style = jsObject {
                        background = if (state.renderMode == mode) "#238636" else "#373e47"
                        color = "white"
                        border = "none"
                        padding = "2px 6px"
                        fontSize = "9px"
                        borderRadius = "3px"
                        cursor = "pointer"
                    }
                }, mode.name.take(4))
            }.toTypedArray(),
            
            // Zoom Controls
            createElement("div", jsObject {
                style = jsObject { marginLeft = "auto"; display = "flex"; gap = "4px" }
            },
                createElement("button", jsObject {
                    onClick = { /* zoom in */ }
                    style = controlButtonStyle()
                }, "+"),
                createElement("span", jsObject {
                    style = jsObject { color = "#8b949e"; fontSize = "9px" }
                }, "${(state.zoom * 100).toInt()}%"),
                createElement("button", jsObject {
                    onClick = { /* zoom out */ }
                    style = controlButtonStyle()
                }, "−")
            )
        )
    }
    
    private fun renderLineSeries(props: Props, state: State): dynamic {
        if (props.series.isEmpty()) {
            return createElement("text", jsObject {
                x = props.width / 2
                y = (props.height - 30) / 2
                textAnchor = "middle"
                fill = "#8b949e"
                fontSize = "12px"
            }, "No data")
        }
        
        val maxValue = props.series.maxOrNull() ?: 1.0
        val minValue = props.series.minOrNull() ?: 0.0
        val range = maxValue - minValue
        
        // Create path for line series
        val pathData = props.series.mapIndexed { index, value ->
            val x = (index.toDouble() / (props.series.size - 1)) * props.width
            val y = ((maxValue - value) / range) * (props.height - 60) + 20
            
            if (index == 0) "M $x $y" else "L $x $y"
        }.joinToString(" ")
        
        return createElement("g", null,
            // Grid lines
            renderGrid(props, state),
            
            // Main line
            createElement("path", jsObject {
                d = pathData
                stroke = "#238636"
                strokeWidth = "2"
                fill = "none"
                filter = "drop-shadow(0 0 4px rgba(35, 134, 54, 0.6))"
            }),
            
            // Data points
            *props.series.mapIndexed { index, value ->
                val x = (index.toDouble() / (props.series.size - 1)) * props.width
                val y = ((maxValue - value) / range) * (props.height - 60) + 20
                
                createElement("circle", jsObject {
                    key = index
                    cx = x
                    cy = y
                    r = if (state.selectedPoint == index) "4" else "2"
                    fill = if (state.selectedPoint == index) "#58a6ff" else "#238636"
                    stroke = "#0d1117"
                    strokeWidth = "1"
                })
            }.toTypedArray(),
            
            // Animation pulse on latest point
            if (props.series.isNotEmpty()) {
                val lastIndex = props.series.size - 1
                val x = (lastIndex.toDouble() / (props.series.size - 1)) * props.width
                val y = ((maxValue - props.series.last()) / range) * (props.height - 60) + 20
                
                createElement("circle", jsObject {
                    cx = x
                    cy = y
                    r = "6"
                    fill = "none"
                    stroke = "#238636"
                    strokeWidth = "2"
                    opacity = "${1.0 - (state.animationFrame % 60) / 60.0}"
                })
            } else null
        )
    }
    
    private fun renderCandlesticks(props: Props, state: State): dynamic {
        // Mock candlestick data from series
        val candlesticks = props.series.windowed(4, 4).mapIndexed { index, window ->
            if (window.size == 4) {
                CandlestickData(
                    open = window[0],
                    high = window.maxOrNull() ?: 0.0,
                    low = window.minOrNull() ?: 0.0,
                    close = window[3],
                    volume = Math.random() * 1000
                )
            } else null
        }.filterNotNull()
        
        if (candlesticks.isEmpty()) {
            return createElement("text", jsObject {
                x = props.width / 2
                y = (props.height - 30) / 2
                textAnchor = "middle"
                fill = "#8b949e"
                fontSize = "12px"
            }, "Insufficient data for candlesticks")
        }
        
        val maxValue = candlesticks.maxOfOrNull { it.high } ?: 1.0
        val minValue = candlesticks.minOfOrNull { it.low } ?: 0.0
        val range = maxValue - minValue
        
        return createElement("g", null,
            renderGrid(props, state),
            
            *candlesticks.mapIndexed { index, candle ->
                val x = (index.toDouble() / (candlesticks.size - 1)) * props.width
                val candleWidth = props.width / candlesticks.size * 0.8
                
                val openY = ((maxValue - candle.open) / range) * (props.height - 60) + 20
                val closeY = ((maxValue - candle.close) / range) * (props.height - 60) + 20
                val highY = ((maxValue - candle.high) / range) * (props.height - 60) + 20
                val lowY = ((maxValue - candle.low) / range) * (props.height - 60) + 20
                
                val isGreen = candle.close > candle.open
                
                createElement("g", jsObject { key = index },
                    // High-Low line
                    createElement("line", jsObject {
                        x1 = x
                        y1 = highY
                        x2 = x
                        y2 = lowY
                        stroke = if (isGreen) "#238636" else "#f85149"
                        strokeWidth = "1"
                    }),
                    
                    // Open-Close body
                    createElement("rect", jsObject {
                        x = x - candleWidth / 2
                        y = min(openY, closeY)
                        width = candleWidth
                        height = abs(openY - closeY).coerceAtLeast(1.0)
                        fill = if (isGreen) "#238636" else "#f85149"
                        opacity = "0.8"
                    })
                )
            }.toTypedArray()
        )
    }
    
    private fun renderHeatmap(props: Props, state: State): dynamic {
        // Convert series to 2D heatmap data
        val gridSize = sqrt(props.series.size.toDouble()).toInt().coerceAtLeast(1)
        val cellWidth = props.width.toDouble() / gridSize
        val cellHeight = (props.height - 30).toDouble() / gridSize
        
        val maxValue = props.series.maxOrNull() ?: 1.0
        val minValue = props.series.minOrNull() ?: 0.0
        
        return createElement("g", null,
            *(0 until gridSize).flatMap { row ->
                (0 until gridSize).map { col ->
                    val index = row * gridSize + col
                    val value = if (index < props.series.size) props.series[index] else 0.0
                    val intensity = if (maxValue > minValue) (value - minValue) / (maxValue - minValue) else 0.0
                    
                    createElement("rect", jsObject {
                        key = "${row}_${col}"
                        x = col * cellWidth
                        y = row * cellHeight + 30
                        width = cellWidth
                        height = cellHeight
                        fill = "hsl(${120 * (1 - intensity)}, 70%, 50%)"
                        opacity = "0.8"
                        stroke = "#30363d"
                        strokeWidth = "0.5"
                    })
                }
            }.toTypedArray()
        )
    }
    
    private fun render3DTensor(props: Props, state: State): dynamic {
        // Mock 3D tensor visualization using isometric projection
        return createElement("g", null,
            createElement("text", jsObject {
                x = props.width / 2
                y = (props.height - 30) / 2
                textAnchor = "middle"
                fill = "#58a6ff"
                fontSize = "14px"
            }, "🎲 3D Tensor View"),
            
            // Cube wireframe
            *listOf(
                // Front face
                "M 100 100 L 200 100 L 200 200 L 100 200 Z",
                // Back face (offset)
                "M 150 50 L 250 50 L 250 150 L 150 150 Z",
                // Connecting lines
                "M 100 100 L 150 50",
                "M 200 100 L 250 50",
                "M 200 200 L 250 150",
                "M 100 200 L 150 150"
            ).mapIndexed { index, path ->
                createElement("path", jsObject {
                    key = index
                    d = path
                    stroke = "#58a6ff"
                    strokeWidth = "1"
                    fill = "none"
                    opacity = "0.6"
                })
            }.toTypedArray()
        )
    }
    
    private fun renderJoinFlow(props: Props, state: State): dynamic {
        return createElement("g", null,
            createElement("text", jsObject {
                x = props.width / 2
                y = (props.height - 30) / 2 - 20
                textAnchor = "middle"
                fill = "#f0f6fc"
                fontSize = "12px"
            }, "Join<A,B> Operations"),
            
            // Join operation visualization
            *props.joinOperations.take(3).mapIndexed { index, joinOp ->
                val y = 60 + index * 40
                createElement("g", jsObject { key = index },
                    // Left operand
                    createElement("rect", jsObject {
                        x = "50"
                        y = y
                        width = "80"
                        height = "25"
                        fill = "#161b22"
                        stroke = "#238636"
                        strokeWidth = "1"
                        rx = "3"
                    }),
                    createElement("text", jsObject {
                        x = "90"
                        y = y + 16
                        textAnchor = "middle"
                        fill = "#c9d1d9"
                        fontSize = "10px"
                    }, joinOp.left),
                    
                    // Join operator
                    createElement("text", jsObject {
                        x = "155"
                        y = y + 16
                        textAnchor = "middle"
                        fill = "#58a6ff"
                        fontSize = "12px"
                    }, "⊗"),
                    
                    // Right operand
                    createElement("rect", jsObject {
                        x = "180"
                        y = y
                        width = "80"
                        height = "25"
                        fill = "#161b22"
                        stroke = "#238636"
                        strokeWidth = "1"
                        rx = "3"
                    }),
                    createElement("text", jsObject {
                        x = "220"
                        y = y + 16
                        textAnchor = "middle"
                        fill = "#c9d1d9"
                        fontSize = "10px"
                    }, joinOp.right),
                    
                    // Arrow
                    createElement("path", jsObject {
                        d = "M 270 ${y + 12} L 290 ${y + 12} M 285 ${y + 8} L 290 ${y + 12} L 285 ${y + 16}"
                        stroke = "#58a6ff"
                        strokeWidth = "2"
                        fill = "none"
                    }),
                    
                    // Result
                    createElement("rect", jsObject {
                        x = "300"
                        y = y
                        width = "80"
                        height = "25"
                        fill = "#161b22"
                        stroke = "#f85149"
                        strokeWidth = "1"
                        rx = "3"
                    }),
                    createElement("text", jsObject {
                        x = "340"
                        y = y + 16
                        textAnchor = "middle"
                        fill = "#c9d1d9"
                        fontSize = "10px"
                    }, joinOp.result)
                )
            }.toTypedArray()
        )
    }
    
    private fun renderGrid(props: Props, state: State): dynamic {
        val gridSpacing = 50
        
        return createElement("g", jsObject {
            opacity = "0.1"
        },
            // Vertical lines
            *(0..props.width step gridSpacing).map { x ->
                createElement("line", jsObject {
                    key = "v$x"
                    x1 = x
                    y1 = "30"
                    x2 = x
                    y2 = props.height
                    stroke = "#30363d"
                    strokeWidth = "1"
                })
            }.toTypedArray(),
            
            // Horizontal lines
            *(30..props.height step gridSpacing).map { y ->
                createElement("line", jsObject {
                    key = "h$y"
                    x1 = "0"
                    y1 = y
                    x2 = props.width
                    y2 = y
                    stroke = "#30363d"
                    strokeWidth = "1"
                })
            }.toTypedArray()
        )
    }
    
    private fun renderJoinOperations(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "join-operations-overlay"
            style = jsObject {
                position = "absolute"
                top = "30px"
                right = "10px"
                background = "rgba(22, 27, 34, 0.9)"
                border = "1px solid #30363d"
                borderRadius = "6px"
                padding = "8px"
                maxWidth = "200px"
            }
        },
            createElement("h5", jsObject {
                style = jsObject {
                    margin = "0 0 8px 0"
                    color = "#58a6ff"
                    fontSize = "11px"
                }
            }, "Live Join Operations"),
            
            *props.joinOperations.takeLast(5).map { joinOp ->
                createElement("div", jsObject {
                    key = joinOp.timestamp
                    style = jsObject {
                        fontSize = "9px"
                        color = "#8b949e"
                        marginBottom = "4px"
                        fontFamily = "monospace"
                    }
                },
                    "${joinOp.type}: ${joinOp.left} ⊗ ${joinOp.right} → ${joinOp.result}"
                )
            }.toTypedArray()
        )
    }
    
    private fun controlButtonStyle() = jsObject {
        background = "#373e47"
        color = "#c9d1d9"
        border = "none"
        width = "16px"
        height = "16px"
        fontSize = "10px"
        cursor = "pointer"
        borderRadius = "2px"
    }
    
    private fun startAnimationLoop(setState: (State) -> Unit, initialState: State) {
        fun animationStep(state: State) {
            setState(state.copy(animationFrame = state.animationFrame + 1))
            window.requestAnimationFrame { animationStep(state) }
        }
        window.requestAnimationFrame { animationStep(initialState) }
    }
    
    private fun handleMouseMove(event: dynamic, props: Props, state: State) {
        // Handle mouse tracking for tooltips/selection
    }
    
    private fun handleClick(event: dynamic, props: Props, state: State) {
        // Handle point selection
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// DATA TYPES
// ═══════════════════════════════════════════════════════════════════════════════════════

data class CandlestickData(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

// ═══════════════════════════════════════════════════════════════════════════════════════
// SERIES ANALYTICS COMPONENT
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Real-time series analytics display
 */
class SeriesAnalytics : ReactComponent<SeriesAnalytics.Props, SeriesAnalytics.State>() {
    
    data class Props(
        val series: List<Double> = emptyList(),
        val windowSize: Int = 20,
        val indicators: List<String> = listOf("SMA", "EMA", "RSI", "MACD")
    )
    
    data class State(
        val calculations: Map<String, List<Double>> = emptyMap(),
        val lastUpdate: Long = 0
    )
    
    override fun createInitialState() = State()
    
    override fun render(props: Props, state: State): dynamic {
        val analytics = calculateAnalytics(props.series, props.windowSize)
        
        return createElement("div", jsObject {
            className = "series-analytics"
            style = jsObject {
                padding = "12px"
                background = "#161b22"
                border = "1px solid #30363d"
                borderRadius = "6px"
                fontSize = "11px"
            }
        },
            createElement("h4", jsObject {
                style = jsObject {
                    margin = "0 0 10px 0"
                    color = "#58a6ff"
                    fontSize = "12px"
                }
            }, "Series Analytics"),
            
            *analytics.map { (key, value) ->
                createElement("div", jsObject {
                    key = key
                    style = jsObject {
                        display = "flex"
                        justifyContent = "space-between"
                        marginBottom = "4px"
                    }
                },
                    createElement("span", jsObject { color = "#8b949e" }, key),
                    createElement("span", jsObject { 
                        color = "#c9d1d9"
                        fontFamily = "monospace"
                    }, value)
                )
            }.toTypedArray()
        )
    }
    
    private fun calculateAnalytics(series: List<Double>, windowSize: Int): List<Pair<String, String>> {
        if (series.isEmpty()) return emptyList()
        
        val latest = series.last()
        val sma = if (series.size >= windowSize) {
            series.takeLast(windowSize).average()
        } else series.average()
        
        val volatility = if (series.size > 1) {
            val returns = series.zipWithNext { a, b -> (b - a) / a }
            sqrt(returns.map { it * it }.average())
        } else 0.0
        
        return listOf(
            "Latest" to String.format("%.2f", latest),
            "SMA($windowSize)" to String.format("%.2f", sma),
            "Min" to String.format("%.2f", series.minOrNull() ?: 0.0),
            "Max" to String.format("%.2f", series.maxOrNull() ?: 0.0),
            "Volatility" to String.format("%.4f", volatility),
            "Count" to series.size.toString()
        )
    }
}