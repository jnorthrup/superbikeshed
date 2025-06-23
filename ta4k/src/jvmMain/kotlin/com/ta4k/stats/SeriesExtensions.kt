package com.ta4k.stats

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.`play`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Extensions for Indexed<BigDecimal> to support statistical calculations
 */
fun Indexed<BigDecimal>.isEmpty(): Boolean = size == 0

fun Indexed<BigDecimal>.sorted(): List<BigDecimal> = `play`.toList().sorted()

fun Indexed<BigDecimal>.filter(predicate: (BigDecimal) -> Boolean): List<BigDecimal> =
    `play`.toList().filter(predicate)

fun Indexed<BigDecimal>.count(predicate: (BigDecimal) -> Boolean): Int =
    `play`.toList().count(predicate)

fun Indexed<BigDecimal>.average(): BigDecimal {
    if (isEmpty()) return BigDecimal.ZERO
    return sumOf { it }.divide(BigDecimal(size), 8, RoundingMode.HALF_UP)
}

fun Indexed<BigDecimal>.sumOf(selector: (BigDecimal) -> BigDecimal): BigDecimal =
    `play`.toList().fold(BigDecimal.ZERO) { acc, value -> acc.add(selector(value)) }
