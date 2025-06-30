package borg.trikeshed.zlib.internal

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
