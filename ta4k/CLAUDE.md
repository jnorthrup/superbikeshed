# Acapulco Migration to ta4k

## Overview

Moving Acapulco code from Trikeshed to ta4k project, focusing on Binance backtest data handling and trading harness.

## Dependencies Required

- Trikeshed (already included in build.gradle.kts)
  - Provides cursors
  - Provides vec
  - Provides QoL utilities (ProgressReporter.Fibonacci, etc.)
- kotlinx-coroutines (added to build.gradle.kts)
- binance-api-client (added to build.gradle.kts)

## Migration Steps

### 1. Core Data Models

- [x] DataBinanceVision.kt
  - Uses Trikeshed dependencies (cursors, vec)
  - Handles Binance data types (aggtrades, klines, trades)

### 2. Data Processing

- [x] HistoryService.kt
  - CSV processing
  - Timestamp conversion
  - Kline data handling

### 3. Event Handling

- [x] TradePairEventMuxer.kt
  - Candlestick event processing
  - Historical data streams
  - ISAM file writing

### 4. Trading Harness

- [x] TradingWallet.kt
  - Account balance tracking
  - Asset balance updates
  - Wallet initialization

### 5. Stream Processing

- [x] Streamer.kt
  - WebSocket client setup
  - Event stream handling
  - User data stream management

### 6. Integration with ta4k

- [ ] Connect with existing Backtester
- [ ] Add Binance-specific data adapters
- [ ] Update test cases

## Current Status

- All core components migrated
- Dependencies set up in build.gradle.kts
- Need to fix linter errors in migrated files
- Need to integrate with ta4k backtesting
- Using Trikeshed's QoL utilities (ProgressReporter.Fibonacci)

## Next Steps

1. Fix linter errors in migrated files:
   - Resolve unresolved references in DataBinanceVision.kt
   - Fix import issues in HistoryService.kt
   - Address dependency issues in TradePairEventMuxer.kt
   - Update TradingWallet.kt to use correct imports
   - Fix Streamer.kt dependencies

2. Add unit tests for migrated components:
   - Test data model conversions
   - Test CSV processing
   - Test event handling
   - Test wallet operations
   - Test stream processing

3. Integrate with existing ta4k backtesting:
   - Create Binance data adapters
   - Connect with Backtester
   - Add example configurations

4. Add documentation:
   - API documentation
   - Usage examples
   - Integration guide

## Component Dependencies

- DataBinanceVision: Base data model
- HistoryService: Depends on DataBinanceVision
- TradePairEventMuxer: Depends on DataBinanceVision, HistoryService
- TradingWallet: Depends on CoinsAndPairings
- Streamer: Depends on TradingWallet, TradePairEventMuxer
