package moneyfan.spacegraph

import borg.trikeshed.lib.*
import moneyfan.core.*
import kotlinx.datetime.Instant

/**
 * SpaceGraph integration for trading visualization
 * Maps market data to spacegraph nodes and trading relationships to edges
 */

@JvmInline
value class NodeId(val value: String)

@JvmInline
value class EdgeId(val value: String)

@JvmInline
value class ChartScale(val value: Float)

enum class NodeType {
    SYMBOL,        // Trading symbol/asset
    CANDLE,        // Price candle
    INDICATOR,     // Technical indicator
    POSITION,      // Portfolio position
    ORDER,         // Trade order
    PORTFOLIO      // Portfolio summary
}

enum class EdgeType {
    PRICE_MOVEMENT,   // Price flow between candles
    CORRELATION,      // Asset correlation
    POSITION_LINK,    // Position to asset link
    INDICATOR_SIGNAL, // Indicator to price signal
    PORTFOLIO_HOLDING // Portfolio contains position
}

data class TradingNode(
    val id: NodeId,
    val type: NodeType,
    val position: Vector3D,
    val data: NodeData
)

data class TradingEdge(
    val id: EdgeId,
    val from: NodeId,
    val to: NodeId,
    val type: EdgeType,
    val strength: Float,
    val data: EdgeData
)

data class NodeData(
    val label: String,
    val value: Double,
    val symbol: String,
    val timestamp: Instant,
    val volume: Double = 0.0,
    val change: Double = 0.0
)

data class EdgeData(
    val weight: Float,
    val direction: Float, // -1.0 to 1.0 for correlation/movement
    val volume: Double = 0.0
)

data class Vector3D(val x: Double, val y: Double, val z: Double)

data class TradingVisualization(
    val nodes: Series<TradingNode>,
    val edges: Series<TradingEdge>,
    val metadata: VisualizationMetadata
)

data class VisualizationMetadata(
    val symbolCount: Int,
    val candleCount: Int,
    val totalValue: Double,
    val timeRange: String,
    val lastUpdate: Instant
)

/**
 * SpaceGraph renderer for trading data
 */
class TradingSpaceGraphRenderer {
    private var lastVisualization: TradingVisualization? = null
    
    fun renderMarketData(
        candleSeries: CandleSeries,
        portfolioState: PortfolioState,
        indicators: Map<String, PriceSeries> = emptyMap()
    ): TradingVisualization {
        
        val nodes = mutableListOf<TradingNode>()
        val edges = mutableListOf<TradingEdge>()
        
        // Create nodes for each symbol's candles
        val candleNodes = createCandleNodes(candleSeries)
        nodes.addAll(candleNodes)
        
        // Create portfolio nodes
        val portfolioNodes = createPortfolioNodes(portfolioState)
        nodes.addAll(portfolioNodes)
        
        // Create indicator nodes
        val indicatorNodes = createIndicatorNodes(indicators)
        nodes.addAll(indicatorNodes)
        
        // Create edges for price movements
        val priceEdges = createPriceMovementEdges(candleNodes)
        edges.addAll(priceEdges)
        
        // Create edges for portfolio positions
        val positionEdges = createPositionEdges(portfolioNodes, candleNodes)
        edges.addAll(positionEdges)
        
        // Create edges for indicator signals
        val indicatorEdges = createIndicatorEdges(indicatorNodes, candleNodes)
        edges.addAll(indicatorEdges)
        
        val metadata = VisualizationMetadata(
            symbolCount = candleSeries.play.map { it.symbol }.distinct().size,
            candleCount = candleSeries.play.size,
            totalValue = portfolioState.totalValue.value,
            timeRange = if (candleSeries.play.isNotEmpty()) {
                "${candleSeries.play.first().startTime} - ${candleSeries.play.last().endTime}"
            } else "No data",
            lastUpdate = kotlinx.datetime.Clock.System.now()
        )
        
        val visualization = TradingVisualization(
            nodes = Series.of(nodes.size) { i -> nodes[i] },
            edges = Series.of(edges.size) { i -> edges[i] },
            metadata = metadata
        )
        
        lastVisualization = visualization
        return visualization
    }
    
    private fun createCandleNodes(candleSeries: CandleSeries): List<TradingNode> {
        val nodes = mutableListOf<TradingNode>()
        
        candleSeries.play.forEachIndexed { index, candle ->
            val priceChange = candle.ohlcv.close.value - candle.ohlcv.open.value
            val isPositive = priceChange >= 0
            
            // Position candles in a time-based layout
            val x = index * 20.0 // Spread along X axis
            val y = candle.ohlcv.close.value // Y = price level
            val z = if (isPositive) 10.0 else -10.0 // Z = positive/negative movement
            
            nodes.add(TradingNode(
                id = NodeId("candle_${candle.symbol.value}_$index"),
                type = NodeType.CANDLE,
                position = Vector3D(x, y, z),
                data = NodeData(
                    label = "${candle.symbol.value} ${candle.ohlcv.close.value}",
                    value = candle.ohlcv.close.value,
                    symbol = candle.symbol.value,
                    timestamp = candle.endTime,
                    volume = candle.ohlcv.volume.value,
                    change = priceChange
                )
            ))
        }
        
        return nodes
    }
    
    private fun createPortfolioNodes(portfolioState: PortfolioState): List<TradingNode> {
        val nodes = mutableListOf<TradingNode>()
        
        // Central portfolio node
        nodes.add(TradingNode(
            id = NodeId("portfolio_center"),
            type = NodeType.PORTFOLIO,
            position = Vector3D(0.0, 0.0, 50.0),
            data = NodeData(
                label = "Portfolio: $${portfolioState.totalValue.value}",
                value = portfolioState.totalValue.value,
                symbol = "PORTFOLIO",
                timestamp = kotlinx.datetime.Clock.System.now()
            )
        ))
        
        // Position nodes around the portfolio
        portfolioState.positions.play.forEachIndexed { index, position ->
            val angle = (index * 2.0 * kotlin.math.PI) / portfolioState.positions.play.size
            val radius = 100.0
            val x = radius * kotlin.math.cos(angle)
            val y = radius * kotlin.math.sin(angle)
            
            nodes.add(TradingNode(
                id = NodeId("position_${position.symbol.value}"),
                type = NodeType.POSITION,
                position = Vector3D(x, y, 30.0),
                data = NodeData(
                    label = "${position.symbol.value}: ${position.quantity}@${position.averagePrice.value}",
                    value = position.totalCost.value,
                    symbol = position.symbol.value,
                    timestamp = kotlinx.datetime.Clock.System.now(),
                    volume = position.quantity.value
                )
            ))
        }
        
        return nodes
    }
    
    private fun createIndicatorNodes(indicators: Map<String, PriceSeries>): List<TradingNode> {
        val nodes = mutableListOf<TradingNode>()
        
        indicators.forEach { (name, series) ->
            if (series.play.isNotEmpty()) {
                val latestValue = series.play.last()
                
                nodes.add(TradingNode(
                    id = NodeId("indicator_$name"),
                    type = NodeType.INDICATOR,
                    position = Vector3D(
                        kotlin.random.Random.nextDouble() * 200.0 - 100.0,
                        latestValue.value,
                        70.0
                    ),
                    data = NodeData(
                        label = "$name: ${latestValue.value}",
                        value = latestValue.value,
                        symbol = name,
                        timestamp = kotlinx.datetime.Clock.System.now()
                    )
                ))
            }
        }
        
        return nodes
    }
    
    private fun createPriceMovementEdges(candleNodes: List<TradingNode>): List<TradingEdge> {
        val edges = mutableListOf<TradingEdge>()
        
        // Connect consecutive candles for the same symbol
        val symbolGroups = candleNodes.groupBy { it.data.symbol }
        
        symbolGroups.forEach { (symbol, nodes) ->
            val sortedNodes = nodes.sortedBy { it.data.timestamp }
            
            for (i in 0 until sortedNodes.size - 1) {
                val current = sortedNodes[i]
                val next = sortedNodes[i + 1]
                
                val priceChange = next.data.value - current.data.value
                val direction = if (priceChange > 0) 1.0f else -1.0f
                val strength = kotlin.math.abs(priceChange).toFloat() / current.data.value.toFloat()
                
                edges.add(TradingEdge(
                    id = EdgeId("price_${symbol}_$i"),
                    from = current.id,
                    to = next.id,
                    type = EdgeType.PRICE_MOVEMENT,
                    strength = strength,
                    data = EdgeData(
                        weight = strength,
                        direction = direction,
                        volume = next.data.volume
                    )
                ))
            }
        }
        
        return edges
    }
    
    private fun createPositionEdges(portfolioNodes: List<TradingNode>, candleNodes: List<TradingNode>): List<TradingEdge> {
        val edges = mutableListOf<TradingEdge>()
        
        val portfolioCenter = portfolioNodes.find { it.type == NodeType.PORTFOLIO }
        val positionNodes = portfolioNodes.filter { it.type == NodeType.POSITION }
        
        portfolioCenter?.let { center ->
            // Connect portfolio to positions
            positionNodes.forEach { position ->
                edges.add(TradingEdge(
                    id = EdgeId("portfolio_${position.data.symbol}"),
                    from = center.id,
                    to = position.id,
                    type = EdgeType.PORTFOLIO_HOLDING,
                    strength = (position.data.value / center.data.value).toFloat(),
                    data = EdgeData(
                        weight = (position.data.value / center.data.value).toFloat(),
                        direction = 1.0f,
                        volume = position.data.volume
                    )
                ))
            }
            
            // Connect positions to their latest candles
            positionNodes.forEach { position ->
                val latestCandle = candleNodes
                    .filter { it.data.symbol == position.data.symbol }
                    .maxByOrNull { it.data.timestamp }
                
                latestCandle?.let { candle ->
                    edges.add(TradingEdge(
                        id = EdgeId("position_candle_${position.data.symbol}"),
                        from = position.id,
                        to = candle.id,
                        type = EdgeType.POSITION_LINK,
                        strength = 1.0f,
                        data = EdgeData(
                            weight = 1.0f,
                            direction = if (candle.data.change >= 0) 1.0f else -1.0f
                        )
                    ))
                }
            }
        }
        
        return edges
    }
    
    private fun createIndicatorEdges(indicatorNodes: List<TradingNode>, candleNodes: List<TradingNode>): List<TradingEdge> {
        val edges = mutableListOf<TradingEdge>()
        
        // Connect indicators to relevant candles (simplified)
        indicatorNodes.forEach { indicator ->
            val relevantCandles = candleNodes.take(5) // Connect to last 5 candles
            
            relevantCandles.forEach { candle ->
                val correlation = kotlin.random.Random.nextFloat() * 2.0f - 1.0f // -1 to 1
                
                edges.add(TradingEdge(
                    id = EdgeId("indicator_${indicator.data.symbol}_${candle.id.value}"),
                    from = indicator.id,
                    to = candle.id,
                    type = EdgeType.INDICATOR_SIGNAL,
                    strength = kotlin.math.abs(correlation),
                    data = EdgeData(
                        weight = kotlin.math.abs(correlation),
                        direction = correlation
                    )
                ))
            }
        }
        
        return edges
    }
}