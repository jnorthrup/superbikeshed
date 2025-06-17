package borg.trikeshed.acapulco

import borg.trikeshed.acapulco.model.Kline
import borg.trikeshed.lib.Series
import borg.trikeshed.lib.j

/**
 * Reads and processes Binance Data Vision archive files.
 * Supports both CSV and compressed formats.
 */
expect class BinanceDataVisionReader() {
    companion object {
        /**
         * Reads a Binance Data Vision archive file and returns a Series of Klines.
         * @param filePath Path to the archive file
         * @return Series of Klines
         */
        fun readArchive(filePath: String): Series<Kline>
    }
} 