@file:OptIn(ExperimentalUnsignedTypes::class)
package borg.trikeshed.lib


/**
 * Empty Indexed singleton - represents an empty indexed sequence
 * 
 * Using the original EmptySeries name to maintain compatibility
 */
object EmptySeries : Indexed<Nothing> by (0 j { x: Int -> TODO("EmptySeries Access Violation at index $x") })