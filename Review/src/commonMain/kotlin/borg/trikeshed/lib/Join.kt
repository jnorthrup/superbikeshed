@file:Suppress("NonAsciiCharacters", "FunctionName", "ObjectPropertyName", "OVERRIDE_BY_INLINE", "UNCHECKED_CAST")

package borg.trikeshed.lib

// This file is now a placeholder or can be removed as Join has been moved to core package.
// If you need to keep this file for compatibility, you can import and re-export the core Join.

import borg.trikeshed.core.Join
import borg.trikeshed.core.j

// Re-exporting for backward compatibility
typealias Join<A, B> = Join<A, B>

// Re-exporting the infix function for backward compatibility
inline infix fun <A, B> A.j(b: B) = j(b)
