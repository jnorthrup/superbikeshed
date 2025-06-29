package borg.trikeshed.wireproto


import borg.trikeshed.lib.*

/**
 * Minimal placeholder manual join overloads for compilation
 */
object ManualJoinOverloads {
    infix fun Int.j(other: Int): Join<Int, Int> = this j other
    infix fun Int.j(other: String): Join<Int, String> = this j other
    infix fun String.j(other: Int): Join<String, Int> = this j other
    infix fun String.j(other: String): Join<String, String> = this j other
} 