package borg.trikeshed.tilting

interface PlatformCodec {
    fun encode(data: ByteArray): ByteArray
    fun decode(data: ByteArray): ByteArray
}

data class Point(
    val x: Double,
    val y: Double
)

data class Window(
    val width: Int,
    val height: Int
)

data class Winsize(
    val ws_row: UShort,
    val ws_col: UShort,
    val ws_xpixel: UShort,
    val ws_ypixel: UShort
) 