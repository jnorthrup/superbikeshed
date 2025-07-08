@file:OptIn(kotlin.RequiresOptIn::class, kotlin.ExperimentalStdlibApi::class)
@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)
package borg.trikeshed.io

expect object IOError {
    fun require(condition: Boolean, message: () -> String)
    fun check(condition: Boolean, message: () -> String): Boolean
} 