package borg.trikeshed.common.collections
@file:OptIn(ExperimentalUnsignedTypes::class)


object _l {
    operator fun <T> get(vararg t: T): List<T> = listOf(*t)
}

