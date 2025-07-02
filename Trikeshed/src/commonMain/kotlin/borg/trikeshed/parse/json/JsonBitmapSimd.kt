// package borg.trikeshed.parse.json
// 
// /**
//  * A multiplatform, SIMD-accelerated engine for creating a structural bitmap of JSON data.
//  *
//  * This `expect` object defines the common API. The `actual` implementations on JVM, Native,
//  * and JS provide platform-specific, optimized code paths.
//  */
// @OptIn(ExperimentalUnsignedTypes::class)
// expect object JsonBitmapSimd {
//     /**
//      * Creates a raw 4-bit-per-byte structural bitmap using platform-native SIMD instructions.
//      * This is the high-performance entry point. The resulting bitmap is processed by
//      * `JsonBitmapProcessor.decodeToStructuralBits`.
//      */
//     fun createBitmap(input: UByteArray): ULongArray
// }