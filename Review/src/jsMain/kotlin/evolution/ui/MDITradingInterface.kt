@file:JsModule("react")
@file:JsNonModule

import borg.trikeshed.lib.*
import evolution.ai.*
import evolution.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.w3c.dom.*
import kotlin.js.*
import kotlin.jvm.*

external val React: dynamic

@file:JsModule("react-dom")
@file:JsNonModule  
external val ReactDOM: dynamic

package evolution.ui

import borg.trikeshed.lib.*
import kotlin.jvm.*

import evolution.ai.*
import evolution.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import org.w3c.dom.*
import kotlin.js.*

/**
 * MDI (Multiple Document Interface) Trading Interface
 * 
 * React-based windowing system for live trading agents with:
 * - JSON interning from CDN for market data
 * - Real-time agent context visualization  
 * - Join/Series/Tensor analytics display
 * - Multi-window agent coordination
 */

// ═══════════════════════════════════════════════════════════════════════════════════════
// REACT COMPONENT BRIDGE
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Kotlin/JS bridge to React for component creation
 */
@JsName("createElement")
external fun createElement(type: String, props: dynamic = definedExternally, vararg children: dynamic): dynamic

@JsName("useState")
external fun <T> useState(initial: T): Array<dynamic>

@JsName("useEffect") 
external fun useEffect(effect: () -> dynamic, deps: Array<dynamic>? = definedExternally)

@JsName("useCallback")
external fun <T> useCallback(callback: T, deps: Array<dynamic>): T

/**
 * React-style component wrapper for Kotlin
 */
abstract class ReactComponent<P : Any, S : Any> {
    abstract fun render(props: P, state: S): dynamic
    
    fun component(props: P): dynamic {
        val (state, setState) = useState(createInitialState())
        
        useEffect({
            onMount(props, state, setState)
            { onUnmount() }
        }, emptyArray())
        
        return render(props, state)
    }
    
    abstract fun createInitialState(): S
    open fun onMount(props: P, state: S, setState: (S) -> Unit) {}
    open fun onUnmount() {}
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// MDI WINDOW SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * MDI Window Manager for trading agent interfaces
 */
class MDIWindowManager : ReactComponent<MDIWindowManager.Props, MDIWindowManager.State>() {
    
    data class Props(
        val orchestrator: MultiAgentTradingOrchestrator,
        val cdnEndpoint: String = "https://api.binance.com/api/v3"
    )
    
    data class State(
        val windows: List<AgentWindow> = emptyList(),
        val activeWindow: String? = null,
        val marketData: List<MarketTick> = emptyList(),
        val layout: WindowLayout = WindowLayout.TILED
    )
    
    enum class WindowLayout { TILED, CASCADE, TABBED, SPLIT }
    
    override fun createInitialState() = State()
    
    override fun onMount(props: Props, state: State, setState: (State) -> Unit) {
        // Start market data feed from CDN
        startCDNDataFeed(props.cdnEndpoint) { tick ->
            setState(state.copy(marketData = state.marketData + tick))
        }
        
        // Wire orchestrator flows
        wireOrchestratorFlows(props.orchestrator, setState, state)
    }
    
    override fun render(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "mdi-container"
            style = jsObject {
                width = "100vw"
                height = "100vh"
                backgroundColor = "#1e1e1e"
                color = "#ffffff"
                fontFamily = "Monaco, 'Courier New', monospace"
                overflow = "hidden"
            }
        },
            // MDI Toolbar
            renderToolbar(props, state),
            
            // Window Container
            createElement("div", jsObject {
                className = "window-container"
                style = jsObject {
                    position = "relative"
                    width = "100%"
                    height = "calc(100% - 40px)"
                    display = when (state.layout) {
                        WindowLayout.TILED -> "grid"
                        WindowLayout.CASCADE -> "relative"
                        WindowLayout.TABBED -> "block"
                        WindowLayout.SPLIT -> "flex"
                    }
                    gridTemplateColumns = if (state.layout == WindowLayout.TILED) {
                        "repeat(auto-fit, minmax(400px, 1fr))"
                    } else "none"
                    gap = "10px"
                    padding = "10px"
                }
            },
                // Render agent windows
                *state.windows.map { window ->
                    renderAgentWindow(window, state.activeWindow == window.agentId)
                }.toTypedArray()
            )
        )
    }
    
    private fun renderToolbar(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "mdi-toolbar"
            style = jsObject {
                height = "40px"
                backgroundColor = "#2d2d2d"
                display = "flex"
                alignItems = "center"
                padding = "0 10px"
                borderBottom = "1px solid #444"
            }
        },
            // New Agent Button
            createElement("button", jsObject {
                onClick = { createNewAgent(props) }
                style = jsObject {
                    backgroundColor = "#4CAF50"
                    color = "white"
                    border = "none"
                    padding = "5px 15px"
                    marginRight = "10px"
                    cursor = "pointer"
                    borderRadius = "3px"
                }
            }, "New Agent"),
            
            // Layout Selector
            createElement("select", jsObject {
                value = state.layout.name
                onChange = { event -> 
                    val layout = WindowLayout.valueOf(event.target.value as String)
                    // setState with new layout
                }
                style = jsObject {
                    backgroundColor = "#3d3d3d"
                    color = "white"
                    border = "1px solid #555"
                    padding = "5px"
                    marginRight = "10px"
                }
            },
                *WindowLayout.values().map { layout ->
                    createElement("option", jsObject {
                        value = layout.name
                    }, layout.name.lowercase().capitalize())
                }.toTypedArray()
            ),
            
            // Market Data Status
            createElement("div", jsObject {
                style = jsObject {
                    marginLeft = "auto"
                    color = if (state.marketData.isNotEmpty()) "#4CAF50" else "#f44336"
                }
            }, "Market Data: ${state.marketData.size} ticks")
        )
    }
    
    private fun renderAgentWindow(window: AgentWindow, isActive: Boolean): dynamic {
        return createElement("div", jsObject {
            key = window.agentId
            className = "agent-window ${if (isActive) "active" else ""}"
            style = jsObject {
                backgroundColor = "#2d2d2d"
                border = if (isActive) "2px solid #4CAF50" else "1px solid #555"
                borderRadius = "5px"
                overflow = "hidden"
                minHeight = "300px"
                display = "flex"
                flexDirection = "column"
            }
        },
            // Window Title Bar
            createElement("div", jsObject {
                className = "window-title"
                style = jsObject {
                    backgroundColor = "#3d3d3d"
                    padding = "8px 12px"
                    borderBottom = "1px solid #555"
                    display = "flex"
                    justifyContent = "space-between"
                    alignItems = "center"
                    cursor = "move"
                }
            },
                createElement("span", null, "Agent: ${window.agentId}"),
                createElement("div", null,
                    createElement("button", jsObject {
                        onClick = { /* minimize */ }
                        style = buttonStyle("#FFA726")
                    }, "−"),
                    createElement("button", jsObject {
                        onClick = { /* close */ }
                        style = buttonStyle("#f44336")
                    }, "×")
                )
            ),
            
            // Window Content
            createElement("div", jsObject {
                className = "window-content"
                style = jsObject {
                    flex = "1"
                    padding = "10px"
                    overflow = "auto"
                }
            },
                renderAgentContent(window)
            )
        )
    }
    
    private fun renderAgentContent(window: AgentWindow): dynamic {
        return createElement("div", null,
            // Agent Status
            createElement("div", jsObject {
                style = jsObject {
                    marginBottom = "15px"
                    padding = "10px"
                    backgroundColor = "#1e1e1e"
                    borderRadius = "3px"
                }
            },
                createElement("h4", jsObject { style = jsObject { margin = "0 0 5px 0" } }, "Status"),
                createElement("p", null, "State: ${window.currentState}"),
                createElement("p", null, "Task: ${window.currentTask ?: "None"}"),
                createElement("p", null, "Signals: ${window.signalCount}")
            ),
            
            // Recent Signals  
            createElement("div", jsObject {
                style = jsObject {
                    marginBottom = "15px"
                }
            },
                createElement("h4", null, "Recent Signals"),
                createElement("div", jsObject {
                    style = jsObject {
                        maxHeight = "150px"
                        overflow = "auto"
                    }
                },
                    *window.recentSignals.map { signal ->
                        createElement("div", jsObject {
                            key = "${signal.timestamp}"
                            style = jsObject {
                                padding = "5px"
                                marginBottom = "5px"
                                backgroundColor = when (signal.type) {
                                    SignalType.BUY -> "#1B5E20"
                                    SignalType.SELL -> "#B71C1C"
                                    SignalType.HOLD -> "#424242"
                                }
                                borderRadius = "3px"
                                fontSize = "12px"
                            }
                        },
                            "${signal.type} ${signal.symbol} (${signal.strength})"
                        )
                    }.toTypedArray()
                )
            ),
            
            // Live Chart Placeholder
            createElement("div", jsObject {
                style = jsObject {
                    height = "200px"
                    backgroundColor = "#1e1e1e"
                    border = "1px solid #555"
                    borderRadius = "3px"
                    display = "flex"
                    alignItems = "center"
                    justifyContent = "center"
                }
            }, "📈 Live Chart (Join/Series/Tensor)")
        )
    }
    
    private fun buttonStyle(color: String) = jsObject {
        backgroundColor = color
        color = "white"
        border = "none"
        width = "20px"
        height = "20px"
        marginLeft = "5px"
        cursor = "pointer"
        borderRadius = "3px"
        fontSize = "12px"
    }
    
    private fun createNewAgent(props: Props) {
        GlobalScope.launch {
            val agentId = "agent_${(Math.random() * 10000).toInt()}"
            val agent = props.orchestrator.createAgent(agentId)
            props.orchestrator.startAgent(agentId)
        }
    }
    
    private fun startCDNDataFeed(endpoint: String, onTick: (MarketTick) -> Unit) {
        // Simulate CDN data feed - in real implementation would use WebSocket
        window.setInterval({
            val symbols = arrayOf("BTC/USDT", "ETH/USDT", "ADA/USDT")
            val symbol = symbols[(Math.random() * symbols.size).toInt()]
            val price = 50000.0 + (Math.random() - 0.5) * 1000
            
            val tick = MarketTick(
                symbol = symbol,
                price = price,
                volume = Math.random() * 100,
                timestamp = Date.now().toLong(),
                bid = price * 0.999,
                ask = price * 1.001
            )
            
            onTick(tick)
        }, 1000)
    }
    
    private fun wireOrchestratorFlows(
        orchestrator: MultiAgentTradingOrchestrator,
        setState: (State) -> Unit,
        state: State
    ) {
        GlobalScope.launch {
            orchestrator.allContextUpdates.collect { update ->
                val window = state.windows.find { it.agentId == update.agentId }
                if (window != null) {
                    val updatedWindow = window.copy(
                        currentState = update.state,
                        lastUpdate = update.timestamp
                    )
                    val updatedWindows = state.windows.map { 
                        if (it.agentId == update.agentId) updatedWindow else it
                    }
                    setState(state.copy(windows = updatedWindows))
                }
            }
        }
        
        GlobalScope.launch {
            orchestrator.allSignals.collect { signal ->
                val window = state.windows.find { it.agentId == signal.agentId }
                if (window != null) {
                    val updatedWindow = window.copy(
                        recentSignals = (listOf(signal) + window.recentSignals).take(10),
                        signalCount = window.signalCount + 1
                    )
                    val updatedWindows = state.windows.map {
                        if (it.agentId == signal.agentId) updatedWindow else it
                    }
                    setState(state.copy(windows = updatedWindows))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// AGENT WINDOW DATA
// ═══════════════════════════════════════════════════════════════════════════════════════

data class AgentWindow(
    val agentId: String,
    val currentState: TradingState = TradingState.INITIALIZING,
    val currentTask: String? = null,
    val signalCount: Int = 0,
    val recentSignals: List<TradingSignal> = emptyList(),
    val lastUpdate: Long = System.currentTimeMillis(),
    val position: Position? = null
)

// ═══════════════════════════════════════════════════════════════════════════════════════
// JOIN/SERIES/TENSOR VISUALIZATION
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * React component for Join/Series/Tensor data visualization
 */
class TensorVisualizationComponent : ReactComponent<TensorVisualizationComponent.Props, TensorVisualizationComponent.State>() {
    
    data class Props(
        val tensorData: dynamic, // JSON-serialized tensor data from CDN
        val series: List<Double> = emptyList(),
        val joinOperations: List<String> = emptyList()
    )
    
    data class State(
        val viewMode: ViewMode = ViewMode.CHART,
        val selectedSeries: String? = null
    )
    
    enum class ViewMode { CHART, TABLE, HEATMAP, TENSOR_3D }
    
    override fun createInitialState() = State()
    
    override fun render(props: Props, state: State): dynamic {
        return createElement("div", jsObject {
            className = "tensor-visualization"
            style = jsObject {
                width = "100%"
                height = "100%"
                display = "flex"
                flexDirection = "column"
            }
        },
            // Visualization Toolbar
            createElement("div", jsObject {
                className = "viz-toolbar"
                style = jsObject {
                    height = "30px"
                    display = "flex"
                    alignItems = "center"
                    gap = "10px"
                    padding = "0 10px"
                    backgroundColor = "#333"
                }
            },
                *ViewMode.values().map { mode ->
                    createElement("button", jsObject {
                        key = mode.name
                        onClick = { /* setState with new mode */ }
                        style = jsObject {
                            backgroundColor = if (state.viewMode == mode) "#4CAF50" else "#555"
                            color = "white"
                            border = "none"
                            padding = "3px 8px"
                            fontSize = "11px"
                            cursor = "pointer"
                        }
                    }, mode.name)
                }.toTypedArray()
            ),
            
            // Visualization Content
            createElement("div", jsObject {
                className = "viz-content"
                style = jsObject {
                    flex = "1"
                    padding = "10px"
                }
            },
                when (state.viewMode) {
                    ViewMode.CHART -> renderChart(props)
                    ViewMode.TABLE -> renderTable(props)
                    ViewMode.HEATMAP -> renderHeatmap(props)
                    ViewMode.TENSOR_3D -> render3DTensor(props)
                }
            )
        )
    }
    
    private fun renderChart(props: Props): dynamic {
        return createElement("div", jsObject {
            style = jsObject {
                width = "100%"
                height = "100%"
                backgroundColor = "#1e1e1e"
                border = "1px solid #555"
                borderRadius = "3px"
                display = "flex"
                alignItems = "center"
                justifyContent = "center"
                flexDirection = "column"
            }
        },
            createElement("div", null, "📈 Series Chart"),
            createElement("div", jsObject {
                style = jsObject { fontSize = "12px"; marginTop = "10px" }
            }, "Join Operations: ${props.joinOperations.size}"),
            // SVG chart would go here in real implementation
            createElement("svg", jsObject {
                width = "300"
                height = "150"
                style = jsObject { marginTop = "10px" }
            },
                createElement("line", jsObject {
                    x1 = "0"; y1 = "75"
                    x2 = "300"; y2 = "75"
                    stroke = "#4CAF50"
                    strokeWidth = "2"
                })
            )
        )
    }
    
    private fun renderTable(props: Props): dynamic {
        return createElement("div", null, "📊 Tensor Table View")
    }
    
    private fun renderHeatmap(props: Props): dynamic {
        return createElement("div", null, "🔥 Tensor Heatmap")
    }
    
    private fun render3DTensor(props: Props): dynamic {
        return createElement("div", null, "🎲 3D Tensor Visualization")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════════════
// MAIN APPLICATION LAUNCHER
// ═══════════════════════════════════════════════════════════════════════════════════════

/**
 * Launch the MDI Trading Interface
 */
class TradingInterfaceLauncher {
    
    fun launch() {
        // Create orchestrator
        val orchestrator = MultiAgentTradingOrchestrator()
        
        // Start market simulation
        GlobalScope.launch {
            orchestrator.startMarketDataSimulation()
            
            // Create some initial agents
            listOf("Alpha", "Beta", "Gamma").forEach { name ->
                orchestrator.createAgent(name)
                orchestrator.startAgent(name)
            }
        }
        
        // Mount React app
        val container = document.getElementById("app")
        val mdiManager = MDIWindowManager()
        
        ReactDOM.render(
            mdiManager.component(MDIWindowManager.Props(orchestrator)),
            container
        )
    }
}

// Utility functions for JS interop
private fun jsObject(init: dynamic.() -> Unit): dynamic {
    val obj = js("{}")
    obj.init()
    return obj
}