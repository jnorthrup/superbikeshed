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
} 