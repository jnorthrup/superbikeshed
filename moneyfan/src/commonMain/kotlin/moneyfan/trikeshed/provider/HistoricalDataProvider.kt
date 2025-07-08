package moneyfan.trikeshed.provider

import kotlinx.datetime.Instant
import kotlin.time.Duration
// moneyfan.trikeshed.context.LatencyProvider import is not directly used by MockHistoricalDataProvider,
// as AbstractSuspendableDataProvider handles the LatencyProvider interaction.
import kotlinx.coroutines.delay // For the internal mock delay in fetchDataInternal

/**
 * Represents a single historical data point, often referred to as a "tick" or a simplified kline.
 * This class serves as a [DataResult] itself if a provider were to return single ticks,
 * but it's primarily used as an element within [HistoricalTicksResult].
 *
 * @property symbol The trading symbol, e.g., "BTCUSDT".
 * @property timestamp The exact [Instant] this data point was recorded.
 * @property price The price at this data point.
 * @property volume Optional trading volume associated with this data point. Defaults to `null`.
 */
data class HistoricalTick(
    val symbol: String,
    val timestamp: Instant,
    val price: Double,
    val volume: Double? = null
) // This could implement DataResult if a provider returned single ticks.

/**
 * Defines a query for retrieving historical tick data for a specific symbol within a given time range.
 * Implements [DataQuery] for use with [SuspendableDataProvider].
 *
 * @property symbol The trading symbol for which data is requested (e.g., "BTCUSDT").
 * @property from The start [Instant] of the query range (inclusive).
 * @property to The end [Instant] of the query range. The provider's implementation
 *              will determine if this is inclusive or exclusive (typically exclusive).
 */
data class HistoricalDataQuery(
    val symbol: String,
    val from: Instant,
    val to: Instant
) : DataQuery

/**
 * Represents the result of a [HistoricalDataQuery], encapsulating a list of [HistoricalTick]s.
 * Implements [DataResult] for use with [SuspendableDataProvider].
 *
 * @property ticks A list of [HistoricalTick] that match the criteria specified in the [HistoricalDataQuery].
 *                 This list may be empty if no data matches the query.
 */
data class HistoricalTicksResult(
    val ticks: List<HistoricalTick>
) : DataResult

/**
 * A mock implementation of [AbstractSuspendableDataProvider] for serving historical tick data.
 *
 * This provider simulates fetching data from a predefined in-memory map (`mockData`).
 * It can also simulate an additional `accessDelay` for each data fetch operation,
 * representing the inherent latency of this particular (mock) data source. This `accessDelay`
 * is applied within `fetchDataInternal`, separate from any delays simulated by a
 * [LatencyProvider][moneyfan.trikeshed.context.LatencyProvider] via the `AbstractSuspendableDataProvider`.
 *
 * @property mockData A map where the key is a symbol string (e.g., "BTCUSDT") and the value is a list of [HistoricalTick]
 *                    for that symbol. For effective range filtering, these lists should ideally be sorted by `timestamp`.
 * @property accessDelay An additional [Duration] to simulate as an internal processing or I/O delay
 *                       each time `fetchDataInternal` is called. This helps in testing how
 *                       consumers handle data source specific latencies. Defaults to 50 milliseconds.
 */
class MockHistoricalDataProvider(
    internal val mockData: Map<String, List<HistoricalTick>>,
    internal val accessDelay: Duration = Duration.milliseconds(50)
) : AbstractSuspendableDataProvider<HistoricalDataQuery, HistoricalTicksResult>() {

    /**
     * Fetches historical ticks from the in-memory `mockData` based on the provided [query].
     *
     * This implementation first simulates the configured `accessDelay`. Then, it retrieves
     * the list of ticks for the requested symbol and filters them based on the `from` (inclusive)
     * and `to` (exclusive) timestamps in the query.
     *
     * @param query The [HistoricalDataQuery] specifying the symbol and time range.
     * @return A [HistoricalTicksResult] containing the list of filtered [HistoricalTick]s.
     *         Returns an empty list within the result if the symbol is not found or no ticks match the time range.
     */
    override suspend fun fetchDataInternal(query: HistoricalDataQuery): HistoricalTicksResult {
        // Simulate the inherent access delay of this specific data source
        if (accessDelay > Duration.ZERO) {
            delay(accessDelay)
        }

        val symbolData = mockData[query.symbol] ?: emptyList()

        // Assuming 'to' is exclusive for the time range query.
        val filteredTicks = symbolData.filter { tick ->
            tick.timestamp >= query.from && tick.timestamp < query.to
        }

        return HistoricalTicksResult(ticks = filteredTicks)
    }

    override fun toString(): String {
        return "MockHistoricalDataProvider(accessDelay=$accessDelay, mockDataKeys=${mockData.keys.joinToString()})"
    }
}
