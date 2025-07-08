package borg.trikeshed.zlib.internal

import borg.trikeshed.lib.Indexed
import borg.trikeshed.lib.Join
import borg.trikeshed.lib.j


// DEFLATE Huffman code maximum lengths
// Literal/Length codes can be up to 15 bits long
const val MAX_BITS_DEFLATE_LIT_LEN = 15

// Distance codes can be up to 15 bits long (though typically shorter in practice)
const val MAX_BITS_DEFLATE_DIST = 15

// Maximum number of bits to peek for initial Huffman table lookup
// This is often chosen as the maximum possible code length for the fastest lookup,
// or a smaller value if a multi-level lookup is used.
// For simplicity in this initial common implementation, we'll use the max possible.
const val HUFFMAN_LOOKUP_BITS_LIT_LEN = MAX_BITS_DEFLATE_LIT_LEN
const val HUFFMAN_LOOKUP_BITS_DIST = MAX_BITS_DEFLATE_DIST

// DEFLATE Length Codes Table
// Each entry is a Join<BaseLength, ExtraBits>
val LENGTH_CODES_TABLE: Indexed<Join<Int, Int>> = Indexed(29) { i ->
    when (i) {
        in 0..3 -> (i + 3) j 0
        4 -> 11 j 1
        5 -> 13 j 1
        6 -> 15 j 1
        7 -> 17 j 1
        8 -> 19 j 2
        9 -> 23 j 2
        10 -> 27 j 2
        11 -> 31 j 2
        12 -> 35 j 3
        13 -> 43 j 3
        14 -> 51 j 3
        15 -> 59 j 3
        16 -> 67 j 4
        17 -> 83 j 4
        18 -> 99 j 4
        19 -> 115 j 4
        20 -> 131 j 5
        21 -> 163 j 5
        22 -> 195 j 5
        23 -> 227 j 5
        24 -> 259 j 0 // Special case: 258 + 1 (no extra bits)
        25 -> 267 j 1
        26 -> 275 j 1
        27 -> 283 j 1
        28 -> 291 j 1
        else -> throw IllegalStateException("Invalid length code index: $i")
    }
}

// DEFLATE Distance Codes Table
// Each entry is a Join<BaseDistance, ExtraBits>
val DISTANCE_CODES_TABLE: Indexed<Join<Int, Int>> = Indexed(30) { i ->
    when (i) {
        in 0..3 -> (i + 1) j 0
        4 -> 5 j 1
        5 -> 7 j 1
        6 -> 9 j 2
        7 -> 13 j 2
        8 -> 17 j 3
        9 -> 25 j 3
        10 -> 33 j 4
        11 -> 49 j 4
        12 -> 65 j 5
        13 -> 97 j 5
        14 -> 129 j 6
        15 -> 193 j 6
        16 -> 257 j 7
        17 -> 385 j 7
        18 -> 513 j 8
        19 -> 769 j 8
        20 -> 1025 j 9
        21 -> 1537 j 9
        22 -> 2049 j 10
        23 -> 3073 j 10
        24 -> 4097 j 11
        25 -> 6145 j 11
        26 -> 8193 j 12
        27 -> 12289 j 12
        28 -> 16385 j 13
        29 -> 24577 j 13
        else -> throw IllegalStateException("Invalid distance code index: $i")
    }
}