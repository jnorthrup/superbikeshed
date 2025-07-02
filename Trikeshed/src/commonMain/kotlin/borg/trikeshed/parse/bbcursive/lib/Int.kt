package borg.trikeshed.parse.bbcursive.lib

import java.nio.ByteBuffer

/**
 * Created by jim on 1/17/16.
 */
object Int {
    fun parseInt(r: ByteBuffer): Int? {
        var x: Long = 0
        var neg = false

        var res: Int? = null
        if (r.hasRemaining()) {
            var i = r.get().toInt()
            when (i) {
                '0'.toInt(), '1'.toInt(), '2'.toInt(), '3'.toInt(), '4'.toInt(), '5'.toInt(), '6'.toInt(), '7'.toInt(), '8'.toInt(), '9'.toInt() -> {
                    x = x * 10 + (i - '0'.toInt())
                }
                '-'.toInt() -> {
                    neg = true
                }
                '+'.toInt() -> {

                }

            }
            while (r.hasRemaining()) {
                i = r.get().toInt()
                when (i) {
                    '0'.toInt(), '1'.toInt(), '2'.toInt(), '3'.toInt(), '4'.toInt(), '5'.toInt(), '6'.toInt(), '7'.toInt(), '8'.toInt(), '9'.toInt() -> {
                        x = x * 10 + (i - '0'.toInt())
                    }
                    '-'.toInt() -> {
                        neg = true
                    }
                    '+'.toInt() -> {

                    }
                    else -> {

                    }
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
            when (i) {
                '0'.toInt(), '1'.toInt(), '2'.toInt(), '3'.toInt(), '4'.toInt(), '5'.toInt(), '6'.toInt(), '7'.toInt(), '8'.toInt(), '9'.toInt() -> {
                    x = x * 10 + (i - '0'.toInt())
                }
                '-'.toInt() -> {
                    neg = true
                }
                '+'.toInt() -> {

                }

            }

            for (j in 1 until length) {
                i = r[j].toInt()
                when (i) {
                    '0'.toInt(), '1'.toInt(), '2'.toInt(), '3'.toInt(), '4'.toInt(), '5'.toInt(), '6'.toInt(), '7'.toInt(), '8'.toInt(), '9'.toInt() -> {
                        x = x * 10 + (i - '0'.toInt())
                    }
                    '-'.toInt() -> {
                        neg = true
                    }
                    '+'.toInt() -> {

                    }
                    else -> {

                    }
                }
            }


            res = (if (neg) -x else x).toInt()
        }
        return res
    }
}