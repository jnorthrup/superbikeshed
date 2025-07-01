package borg.trikeshed.parse.bbcursive.lib

import borg.trikeshed.lib.ByteIndexedBuffer

/**
 * Created by jim on 1/17/16.
 */
object Int { // Renamed to IntObject to avoid conflict with Kotlin's Int type
    fun parseInt(r: ByteIndexedBuffer): Int? { // Changed ByteBuffer to ByteIndexedBuffer
        var x: Long = 0
        var neg = false

        var res: Int? = null
        if (r.hasRemaining) {
            var i = r.get().toInt()
            when (i.toChar()) {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> x = x * 10 + (i - '0'.toInt())
                '-' -> neg = true
                '+' -> {}
            }
            while (r.hasRemaining) {
                i = r.get().toInt()
                when (i.toChar()) {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> x = x * 10 + (i - '0'.toInt())
                    '-' -> neg = true
                    '+' -> {}
                }
            }
            res = (if (neg) -x else x).toInt()
        }
        return res
    }

    fun parseInt(r: String): Int? {
        var x: Long = 0
        var neg = false

        var res: Int? = null

        val length = r.length
        if (0 < length) {
            var i = r[0].toInt()
            when (i.toChar()) {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> x = x * 10 + (i - '0'.toInt())
                '-' -> neg = true
                '+' -> {}
            }

            for (j in 1 until length) {
                i = r[j].toInt()
                when (i.toChar()) {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> x = x * 10 + (i - '0'.toInt())
                    '-' -> neg = true
                    '+' -> {}
                }
            }
            res = (if (neg) -x else x).toInt()
        }
        return res
    }
}
