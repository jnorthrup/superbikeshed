@file:Suppress("UNCHECKED_CAST", "ObjectPropertyName")
@file:OptIn(kotlin.experimental.ExperimentalTypeInference::class, ExperimentalUnsignedTypes::class)

package borg.trikeshed.lib

// This file is now a placeholder or can be removed as Series has been moved to core package.
// If you need to keep this file for compatibility, you can import and re-export the core Series.

import borg.trikeshed.core.Series
import borg.trikeshed.core.α
import borg.trikeshed.core.`▶`
import borg.trikeshed.core.toSeries
import borg.trikeshed.core.emptySeries
// ... other imports as needed for re-exporting

// Re-exporting for backward compatibility
typealias Series<T> = Series<T>

// Re-exporting key functions and properties for backward compatibility
val <T> Series<T>.size: Int get() = size

inline infix fun <X, C, V : Series<X>> V.α(crossinline xform: (X) -> C): Series<C> = α(xform)

// Add other re-exports as necessary for compatibility
