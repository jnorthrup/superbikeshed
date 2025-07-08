package ta4k.cascading

import borg.trikeshed.cursor.*
import borg.trikeshed.lib.*

/**
 * CouchDB-style views for Technical Analysis
 * 
 * The cascading MapReduce pattern is PERFECT for financial data:
 * - Tick → Minute → Hour → Day → Week → Month → Year
 * - Preserving OHLCV integrity through all timeframes
 */

// CouchDB View Definitions (as Kotlin DSL)

object TAViews {
    
    /**
     * View: by_symbol_time
     * Cascading OHLCV aggregation with statistical preservation
     */
    val bySymbolTime = CouchView(
        map = """
            function(doc) {
                if (doc.type === 'tick' && doc.symbol && doc.timestamp) {
                    var date = new Date(doc.timestamp);
                    emit([
                        doc.symbol,
                        date.getFullYear(),
                        date.getMonth() + 1,
                        date.getDate(),
                        date.getHours(),
                        date.getMinutes(),
                        date.getSeconds()
                    ], {
                        price: doc.price,
                        volume: doc.volume,
                        bid: doc.bid,
                        ask: doc.ask
                    });
                }
            }
        """,
        reduce = """
            function(keys, values, rereduce) {
                var result = {
                    open: 0,
                    high: -Infinity,
                    low: Infinity,
                    close: 0,
                    volume: 0,
                    vwap_numerator: 0,  // For volume-weighted average price
                    tick_count: 0,
                    timestamp_first: Infinity,
                    timestamp_last: -Infinity
                };
                
                if (!rereduce) {
                    // First reduce - process raw ticks
                    values.forEach(function(v, i) {
                        var timestamp = keys[i][0][1]; // Extract timestamp from key
                        
                        // OHLC logic
                        if (timestamp < result.timestamp_first) {
                            result.timestamp_first = timestamp;
                            result.open = v.price;
                        }
                        if (timestamp > result.timestamp_last) {
                            result.timestamp_last = timestamp;
                            result.close = v.price;
                        }
                        
                        result.high = Math.max(result.high, v.price);
                        result.low = Math.min(result.low, v.price);
                        result.volume += v.volume;
                        result.vwap_numerator += v.price * v.volume;
                        result.tick_count += 1;
                    });
                } else {
                    // Re-reduce - combine pre-aggregated OHLCV
                    values.forEach(function(v) {
                        // Preserve OHLC integrity through cascade
                        if (v.timestamp_first < result.timestamp_first) {
                            result.timestamp_first = v.timestamp_first;
                            result.open = v.open;
                        }
                        if (v.timestamp_last > result.timestamp_last) {
                            result.timestamp_last = v.timestamp_last;
                            result.close = v.close;
                        }
                        
                        result.high = Math.max(result.high, v.high);
                        result.low = Math.min(result.low, v.low);
                        result.volume += v.volume;
                        result.vwap_numerator += v.vwap_numerator;
                        result.tick_count += v.tick_count;
                    });
                }
                
                // Calculate VWAP
                result.vwap = result.volume > 0 ? 
                    result.vwap_numerator / result.volume : 0;
                
                return result;
            }
        """
    )
    
    /**
     * View: technical_indicators
     * Cascading technical indicator calculations
     */
    val technicalIndicators = CouchView(
        map = """
            function(doc) {
                if (doc.type === 'ohlcv' && doc.symbol) {
                    emit([doc.symbol, doc.timeframe, doc.timestamp], {
                        high: doc.high,
                        low: doc.low,
                        close: doc.close,
                        volume: doc.volume,
                        // Pre-calculate for indicators
                        typical_price: (doc.high + doc.low + doc.close) / 3,
                        money_flow: ((doc.high + doc.low + doc.close) / 3) * doc.volume
                    });
                }
            }
        """,
        reduce = """
            function(keys, values, rereduce) {
                // Cascading calculation of technical indicators
                var sma_sum = 0, ema_prev = 0;
                var rsi_gains = 0, rsi_losses = 0;
                var highest = -Infinity, lowest = Infinity;
                var volume_sum = 0, money_flow_sum = 0;
                var count = values.length;
                
                values.forEach(function(v) {
                    sma_sum += v.close || v.sma_sum || 0;
                    volume_sum += v.volume || v.volume_sum || 0;
                    money_flow_sum += v.money_flow || v.money_flow_sum || 0;
                    highest = Math.max(highest, v.high || v.highest || -Infinity);
                    lowest = Math.min(lowest, v.low || v.lowest || Infinity);
                    
                    // RSI calculation
                    if (v.close_change > 0) rsi_gains += v.close_change;
                    else rsi_losses += Math.abs(v.close_change);
                });
                
                return {
                    sma: sma_sum / count,
                    volume_sma: volume_sum / count,
                    mfi: money_flow_sum / volume_sum,  // Money Flow Index component
                    highest: highest,
                    lowest: lowest,
                    count: count,
                    // Preserve for re-reduce
                    sma_sum: sma_sum,
                    volume_sum: volume_sum,
                    money_flow_sum: money_flow_sum
                };
            }
        """
    )
}