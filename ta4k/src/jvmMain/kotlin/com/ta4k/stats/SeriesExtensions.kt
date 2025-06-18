package com.ta4k.stats

import borg.trikeshed.lib.Series
import borg.trikeshed.lib.`play`
import borg.trikeshed.lib.size
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Extensions for Series<BigDecimal> to support statistical calculations
 */
fun Series<BigDecimal>.isEmpty(): Boolean = size == 0

fun Series<BigDecimal>.sorted(): List<BigDecimal> = `play`.toList().sorted()

fun Series<BigDecimal>.filter(predicate: (BigDecimal) -> Boolean): List<BigDecimal> = 
    `play`.toList().filter(predicate)

fun Series<BigDecimal>.count(predicate: (BigDecimal) -> Boolean): Int =
    `play`.toList().count(predicate)

fun Series<BigDecimal>.average(): BigDecimal {
    if (isEmpty()) return BigDecimal.ZERO
    return sumOf { it }.divide(BigDecimal(size), 8, RoundingMode.HALF_UP)
}

fun Series<BigDecimal>.sumOf(selector: (BigDecimal) -> BigDecimal): BigDecimal =
    `play`.toList().fold(BigDecimal.ZERO) { acc, value -> acc.add(selector(value)) }
