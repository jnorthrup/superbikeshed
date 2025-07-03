package borg.trikeshed.common

// Pure utility functions - no platform dependencies
import borg.trikeshed.lib.*

fun <T> List<T>.toIndexed(): Indexed<T> = 
    this.size j { i -> this[i] }

fun <T> Array<T>.toIndexed(): Indexed<T> = 
    this.size j { i -> this[i] } 