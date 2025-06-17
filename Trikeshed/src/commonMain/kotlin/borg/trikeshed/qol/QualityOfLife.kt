package borg.trikeshed.qol

import kotlin.experimental.ExperimentalUnsignedTypes

/**
 * Quality of Life (QoL) package that sanctifies core utilities for making the codebase
 * more pleasant and efficient to work with. These are fundamental building blocks that
 * provide significant developer experience benefits.
 */
object QualityOfLife {
    /**
     * Zero/Non-Zero scanning utilities - fundamental boolean checks
     */
    object ZeroScan {
        /**
         * Extension property for zero check
         */
        val Int.z: Boolean get() = this == 0

        /**
         * Extension property for non-zero check
         */
        val Int.nz: Boolean get() = this != 0

        /**
         * Extension property for zero check on nullable
         */
        val Int?.z: Boolean get() = this == 0

        /**
         * Extension property for non-zero check on nullable
         */
        val Int?.nz: Boolean get() = this != 0 && this != null
    }

    /**
     * Type-safe null handling utilities
     */
    object NullSafe {
        /**
         * Safely unwrap a nullable value with a default
         */
        fun <T> T?.or(default: T): T = this ?: default

        /**
         * Safely unwrap a nullable value with a computed default
         */
        fun <T> T?.orCompute(default: () -> T): T = this ?: default()

        /**
         * Safely transform a nullable value
         */
        fun <T, R> T?.transform(transform: (T) -> R): R? = this?.let(transform)
    }

    /**
     * Collection utilities for common operations
     */
    object CollectionUtils {
        /**
         * Check if a collection is empty
         */
        fun <T> Collection<T>?.isEmpty(): Boolean = this == null || this.isEmpty()

        /**
         * Check if a collection is not empty
         */
        fun <T> Collection<T>?.isNotEmpty(): Boolean = this != null && this.isNotEmpty()

        /**
         * Get first element or null
         */
        fun <T> Collection<T>?.firstOrNull(): T? = this?.firstOrNull()

        /**
         * Get last element or null
         */
        fun <T> Collection<T>?.lastOrNull(): T? = this?.lastOrNull()
    }

    /**
     * String utilities for common operations
     */
    object StringUtils {
        /**
         * Check if string is empty or null
         */
        fun String?.isEmpty(): Boolean = this == null || this.isEmpty()

        /**
         * Check if string is not empty and not null
         */
        fun String?.isNotEmpty(): Boolean = this != null && this.isNotEmpty()

        /**
         * Check if string is blank or null
         */
        fun String?.isBlank(): Boolean = this == null || this.isBlank()

        /**
         * Check if string is not blank and not null
         */
        fun String?.isNotBlank(): Boolean = this != null && this.isNotBlank()
    }

    /**
     * Progress reporting utilities using Fibonacci sequence for natural-feeling progress updates
     */
    object ProgressReporter {
        /**
         * Creates a Fibonacci-based progress reporter for a specific type of item
         * @param noun The type of items being processed (e.g., "files", "records")
         */
        class Fibonacci(val noun: String = "items") {
            private var lastReported = 0
            private val fibonacci = sequence {
                var a = 1
                var b = 1
                while (true) {
                    yield(a)
                    val next = a + b
                    a = b
                    b = next
                }
            }.iterator()

            /**
             * Reports progress if the current count meets the next Fibonacci threshold
             * @param count Current number of items processed
             * @return Progress message if threshold reached, null otherwise
             */
            fun report(count: Int): String? {
                val nextThreshold = fibonacci.next()
                return if (count >= nextThreshold && count > lastReported) {
                    lastReported = count
                    "Processed $count $noun"
                } else null
            }
        }
    }
} 