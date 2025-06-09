# ta4k

This project is designed to fetch, store, and process financial market data, specifically klines (candlestick data), from cryptocurrency exchanges like Binance.

The primary purpose of this data is to facilitate technical analysis and the backtesting of trading strategies.

The shell scripts located in the `bin/` directory are used to automate the process of:
- Fetching historical kline data for various cryptocurrency pairs.
- Processing and combining the downloaded data into a usable format.

The project aims to provide a foundation for building more complex trading tools and analysis platforms using Kotlin and potentially other multiplatform targets.

## Domain Overview

This diagram provides a map of the Technical Analysis (TA) and quantitative tooling domain, highlighting how this project's data layer (`TrikeShed` / `columnar`) fits in.

```mermaid
graph TD;
    subgraph "Data Layer"
        DS[Data Sources] --> DI;
        DS_Klines[Klines] --> DS;
        DS_OrderBook[Order Book] --> DS;
        DS_News[News/Sentiment] --> DS;
        DS_Alt[Alternative Data] --> DS;

        DI("TrikeShed / columnar
        [Functional, Immutable Guarantees]
        [Economically Small Codebase]
        [Bare Metal ISAM Data]");
    end

    subgraph "Analytics & Modeling Layer"
        DI --> FE[Feature Engineering];
        FE --> TA[Classical TA Indicators
        (MA, RSI, MACD, Bollinger)];
        FE --> SM[Statistical Models (Numerics)
        (ARIMA, GARCH, Kalman Filters)];
        FE --> MLDL[Machine Learning / Deep Learning
        (RBFN, LSTMs, Transformers)
        <br/><em>Established & Adapted for Finance</em>];
    end

    subgraph "Strategy & Execution Layer"
        TA --> StratDev[Strategy Development
        (Signal Logic, Portfolio Construction)];
        SM --> StratDev;
        MLDL --> StratDev;
        StratDev --> BT[Backtesting
        (Event-Driven, Vectorized)];
        StratDev --> RM[Risk Management
        (Volatility Models, Position Sizing, Drawdown Control)];
        RM --> Exec[Execution
        (Broker APIs, Order Types)];
        BT --> RM;
    end

    classDef trikeshedCls fill:#f9f,stroke:#333,stroke-width:2px,color:#000;
    class DI trikeshedCls;
```
