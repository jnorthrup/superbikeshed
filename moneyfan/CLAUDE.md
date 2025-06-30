# MoneyFan Project Instructions

## Trading Architecture

MoneyFan implements trading bot capabilities with TrikeShed data structures.

## Data Structure Patterns

- Use `Series<T>` for time-series data (price history, trading signals)
- Use `Join<A,B>` for asset mappings and portfolio relationships
- Prefer primitive arrays for high-frequency trading data
- Follow TrikeShed type system patterns

## Trading Components

- **Portfolio Management**: Use Series for asset allocation tracking
- **Market Data**: Series for candlestick data, order books
- **Signal Processing**: Join for symbol-to-signal mappings
- **Risk Management**: Series for risk metrics over time

## Performance Considerations

- for loops are the gold standard for performance-critical trading logic
- Avoid List<T> and Pair<A,B> in hot paths
- Use primitive arrays for tick data processing
- Leverage TrikeShed's cache-friendly data structures

## Integration Guidelines

- Connect with ta4k for technical analysis
- Use Acapulco patterns for data processing pipelines
- Follow museum preservation rules for trading algorithms