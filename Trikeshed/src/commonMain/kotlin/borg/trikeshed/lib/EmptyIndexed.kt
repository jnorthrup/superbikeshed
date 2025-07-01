package borg.trikeshed.lib


/**
 * Empty Indexed singleton - represents an empty indexed sequence
 * 
 * Using the original EmptyIndexed name to maintain compatibility
 */
object EmptyIndexed : Indexed<Nothing> by (0 j { x: Int -> TODO("EmptyIndexed Access Violation at index $x") })