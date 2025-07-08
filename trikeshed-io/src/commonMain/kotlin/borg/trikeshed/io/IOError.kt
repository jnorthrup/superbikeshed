@file:OptIn(RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class, kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

expect object IOError {
    fun require(condition: Boolean, message: () -> String)
    fun check(condition: Boolean, message: () -> String): Boolean
} 