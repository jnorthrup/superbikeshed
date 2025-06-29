package borg.trikeshed.multiformats

/**
 * Known hash function types used in Multihash.
 * Codes are from the multicodec table.
 * @property code The integer code for the hash function.
 * @property defaultSizeInBytes The standard digest size for this hash function in bytes. Can be 0 if variable or not typically checked.
 */
enum class HashType(val code: Int, val defaultSizeInBytes: Int) {
    SHA2_256(0x12, 32)
    // SHA1(0x11, 20), // Example: if needed later
    // SHA3_256(0x16, 32), // Example
    // Add other common hash types as they become supported/needed
}

/**
 * Known content types used in CIDv1 (Multicodec codes).
 * Codes are from the multicodec table.
 * @property code The integer code for the content type.
 */
enum class ContentType(val code: Int) {
    RAW(0x55),          // raw binary data
    DAG_PB(0x70),       // Protobuf DAG Node (used in CIDv0 implicitly)
    LIBP2P_KEY(0x72)    // libp2p public key
    // DAG_CBOR(0x71),     // Example: if needed later
    // Add other common content types as they become supported/needed
}
